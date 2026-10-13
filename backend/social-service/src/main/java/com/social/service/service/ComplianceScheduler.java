package com.social.service.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.social.service.config.AppProperties;
import com.social.service.config.SensitiveWordFilter;
import com.social.service.entity.Moment;
import com.social.service.entity.UserLog;
import com.social.service.entity.UserProfile;
import com.social.service.mapper.MomentMapper;
import com.social.service.mapper.UserLogMapper;
import com.social.service.mapper.UserProfileMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 合规定时任务（阶段七）：
 * 1. 操作日志留存 180 天后清理（《网络安全法》最低留存要求）；
 * 2. 定期巡检已发布动态，命中敏感词（词库更新后）回退为待审核。
 * <p>prod 多实例部署时可用 {@code app.compliance.job-enabled=false} 关闭，避免重复扫描；
 * 默认开启（matchIfMissing）。*/
@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(prefix = "app.compliance", name = "job-enabled", havingValue = "true", matchIfMissing = true)
public class ComplianceScheduler {

    private final UserLogMapper userLogMapper;
    private final MomentMapper momentMapper;
    private final UserProfileMapper userProfileMapper;
    private final SensitiveWordFilter sensitiveWordFilter;
    private final AppProperties properties;
    private final UserLogService userLogService;

    /** 每天凌晨 3:05 清理 180 天前的操作日志。 */
    @Scheduled(cron = "0 5 3 * * ?")
    public void purgeExpiredLogs() {
        LocalDateTime cutoff = LocalDateTime.now().minusDays(180);
        int deleted = userLogMapper.delete(new LambdaQueryWrapper<UserLog>()
                .lt(UserLog::getCreatedAt, cutoff));
        if (deleted > 0) {
            log.info("[compliance] 清理 {} 天前操作日志 {} 条", 180, deleted);
        }
    }

    /** 每 30 分钟巡检已发布动态，命中敏感词回退为待审核（先发后审兜底）。 */
    @Scheduled(fixedDelay = 30 * 60 * 1000, initialDelay = 60 * 1000)
    public void rescanPublishedMoments() {
        long fromId = 0;
        int scanned = 0;
        int flagged = 0;
        for (int batch = 0; batch < 100; batch++) {
            List<Moment> moments = momentMapper.selectList(new LambdaQueryWrapper<Moment>()
                    .eq(Moment::getStatus, Moment.STATUS_PUBLISHED)
                    .gt(Moment::getId, fromId)
                    .orderByAsc(Moment::getId)
                    .last("LIMIT 200"));
            if (moments.isEmpty()) {
                break;
            }
            for (Moment m : moments) {
                scanned++;
                if (sensitiveWordFilter.contains(m.getContent())) {
                    momentMapper.update(null, new LambdaUpdateWrapper<Moment>()
                            .eq(Moment::getId, m.getId())
                            .set(Moment::getStatus, Moment.STATUS_PENDING));
                    flagged++;
                }
            }
            fromId = moments.get(moments.size() - 1).getId();
            if (moments.size() < 200) {
                break;
            }
        }
        if (flagged > 0) {
            log.info("[compliance] 巡检扫描 {} 条已发布动态，{} 条命中敏感词回退待审核", scanned, flagged);
        }
    }

    /** 每 30 分钟巡检昵称：命中敏感词的自动改为「违禁昵称+时间戳」（管理员可在后台再行改名）。 */
    @Scheduled(fixedDelay = 30 * 60 * 1000, initialDelay = 120 * 1000)
    public void rescanNicknames() {
        String prefix = properties.getNickname().getBannedPrefix();
        long fromId = 0;
        int scanned = 0;
        int renamed = 0;
        for (int batch = 0; batch < 100; batch++) {
            List<UserProfile> profiles = userProfileMapper.selectList(new LambdaQueryWrapper<UserProfile>()
                    .gt(UserProfile::getId, fromId)
                    .orderByAsc(UserProfile::getId)
                    .last("LIMIT 200"));
            if (profiles.isEmpty()) {
                break;
            }
            for (UserProfile p : profiles) {
                scanned++;
                if (p.getNickname() != null && sensitiveWordFilter.contains(p.getNickname())) {
                    p.setNickname(prefix + System.currentTimeMillis());
                    userProfileMapper.updateById(p);
                    renamed++;
                    userLogService.write(p.getUserId(), "nicknameReset", "昵称含敏感词，自动改为 " + p.getNickname());
                }
            }
            fromId = profiles.get(profiles.size() - 1).getId();
            if (profiles.size() < 200) {
                break;
            }
        }
        if (renamed > 0) {
            log.info("[compliance] 昵称巡检扫描 {} 个用户，{} 个昵称命中敏感词自动改名", scanned, renamed);
        }
    }
}
