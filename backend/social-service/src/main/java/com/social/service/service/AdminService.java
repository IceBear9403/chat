package com.social.service.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.social.common.constant.RedisKeys;
import com.social.common.enums.ErrorCode;
import com.social.common.exception.BusinessException;
import com.social.common.util.DigestUtil;
import com.social.common.util.PiiCrypto;
import com.social.service.config.AppProperties;
import com.social.service.dto.AuditMomentVO;
import com.social.service.dto.MomentMediaVO;
import com.social.service.dto.PageVO;
import com.social.service.dto.ReportAdminVO;
import com.social.service.dto.UserAdminVO;
import com.social.service.dto.UserLogVO;
import com.social.service.entity.Moment;
import com.social.service.entity.MomentMedia;
import com.social.service.entity.Report;
import com.social.service.entity.User;
import com.social.service.entity.UserLog;
import com.social.service.entity.UserProfile;
import com.social.service.mapper.MomentMapper;
import com.social.service.mapper.MomentMediaMapper;
import com.social.service.mapper.ReportMapper;
import com.social.service.mapper.UserLogMapper;
import com.social.service.mapper.UserMapper;
import com.social.service.mapper.UserProfileMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * 管理端服务（阶段七合规：内容审核、举报处理、用户封禁、操作日志）。
 * 所有入口先 {@link #requireAdmin} 校验管理员身份（user.is_admin=1）。
 */
@Service
@RequiredArgsConstructor
public class AdminService {

    private final UserMapper userMapper;
    private final UserProfileMapper userProfileMapper;
    private final MomentMapper momentMapper;
    private final MomentMediaMapper momentMediaMapper;
    private final ReportMapper reportMapper;
    private final UserLogMapper userLogMapper;
    private final StringRedisTemplate redis;
    private final AppProperties properties;
    private final UserLogService userLogService;

    /** 校验当前用户为管理员，否则抛 403。 */
    public void requireAdmin(Long userId) {
        if (userId == null) {
            throw new BusinessException(ErrorCode.FORBIDDEN, "无管理权限");
        }
        User user = userMapper.selectById(userId);
        if (user == null || !Integer.valueOf(1).equals(user.getIsAdmin()) || user.getStatus() != 0) {
            throw new BusinessException(ErrorCode.FORBIDDEN, "无管理权限");
        }
    }

    // ==================== 用户管理 ====================

    public PageVO<UserAdminVO> listUsers(Long adminId, String keyword, Integer status, int page, int size) {
        requireAdmin(adminId);
        int p = Math.max(page, 1);
        int s = Math.min(Math.max(size, 1), 50);

        LambdaQueryWrapper<User> wrapper = new LambdaQueryWrapper<>();
        if (status != null) {
            wrapper.eq(User::getStatus, status);
        }
        // 关键字：完整手机号按 hash 精确匹配，或昵称 LIKE（先查 profile 再按 id 过滤）
        if (keyword != null && !keyword.isBlank()) {
            String kw = keyword.trim();
            List<Long> ids = new ArrayList<>();
            if (kw.matches("^1[3-9]\\d{9}$")) {
                User byPhone = userMapper.selectOne(new LambdaQueryWrapper<User>()
                        .eq(User::getPhoneHash, DigestUtil.sha256Hex(kw)));
                if (byPhone != null) {
                    ids.add(byPhone.getId());
                }
            }
            if (kw.contains("@")) {
                User byEmail = userMapper.selectOne(new LambdaQueryWrapper<User>()
                        .eq(User::getEmailHash, DigestUtil.sha256Hex(kw.toLowerCase())));
                if (byEmail != null) {
                    ids.add(byEmail.getId());
                }
            }
            userProfileMapper.selectList(new LambdaQueryWrapper<UserProfile>()
                            .like(UserProfile::getNickname, kw)
                            .last("LIMIT 50"))
                    .forEach(up -> ids.add(up.getUserId()));
            if (ids.isEmpty()) {
                PageVO<UserAdminVO> empty = new PageVO<>();
                empty.setRecords(List.of());
                empty.setPage(p);
                empty.setSize(s);
                empty.setTotal(0);
                empty.setHasMore(false);
                return empty;
            }
            wrapper.in(User::getId, ids.stream().distinct().collect(Collectors.toList()));
        }
        wrapper.orderByDesc(User::getId);

        long total = userMapper.selectCount(wrapper);
        List<User> users = userMapper.selectList(wrapper.last("LIMIT " + (p - 1) * s + ", " + s));
        Map<Long, UserProfile> profileMap = profilesOf(users.stream().map(User::getId).collect(Collectors.toSet()));

        List<UserAdminVO> records = users.stream().map(u -> {
            UserProfile profile = profileMap.get(u.getId());
            UserAdminVO vo = new UserAdminVO();
            vo.setId(u.getId());
            vo.setPhone(maskPhone(PiiCrypto.decrypt(u.getPhone(), piiKey())));
            vo.setEmail(maskEmail(PiiCrypto.decrypt(u.getEmail(), piiKey())));
            vo.setNickname(profile == null ? null : profile.getNickname());
            vo.setAvatar(profile == null ? null : profile.getAvatar());
            vo.setStatus(u.getStatus());
            vo.setRealnameStatus(profile == null ? 0 : profile.getRealnameStatus());
            vo.setIsAdmin(Integer.valueOf(1).equals(u.getIsAdmin()));
            vo.setCreatedAt(u.getCreatedAt());
            return vo;
        }).collect(Collectors.toList());

        return pageOf(records, p, s, total);
    }

    /** 封禁 / 解封用户：更新状态并写 Redis 封禁标记，使在线会话立即失效。 */
    @Transactional
    public void setUserStatus(Long adminId, Long userId, Integer status) {
        requireAdmin(adminId);
        User user = userMapper.selectById(userId);
        if (user == null) {
            throw new BusinessException(ErrorCode.USER_NOT_EXIST);
        }
        if (Integer.valueOf(1).equals(user.getIsAdmin())) {
            throw new BusinessException(ErrorCode.FORBIDDEN, "不能封禁管理员");
        }
        if (status == 1) {
            user.setStatus(1);
            redis.opsForValue().set(RedisKeys.userBanKey(userId), "1");
            userLogService.write(adminId, "ban", "封禁用户 " + userId);
        } else if (status == 0) {
            user.setStatus(0);
            redis.delete(RedisKeys.userBanKey(userId));
            userLogService.write(adminId, "unban", "解封用户 " + userId);
        } else {
            throw new BusinessException(ErrorCode.PARAM_ERROR, "状态参数不正确");
        }
        userMapper.updateById(user);
    }

    /** 管理员修改任意用户昵称（不受昵称修改间隔 / 敏感词限制，管理员可随意改名）。 */
    @Transactional
    public void setUserNickname(Long adminId, Long userId, String nickname) {
        requireAdmin(adminId);
        if (nickname == null || nickname.isBlank()) {
            throw new BusinessException(ErrorCode.PARAM_ERROR, "昵称不能为空");
        }
        String trimmed = nickname.trim();
        if (trimmed.length() > 20) {
            throw new BusinessException(ErrorCode.PARAM_ERROR, "昵称最长 20 个字符");
        }
        UserProfile profile = userProfileMapper.selectOne(
                new LambdaQueryWrapper<UserProfile>().eq(UserProfile::getUserId, userId));
        if (profile == null) {
            throw new BusinessException(ErrorCode.USER_NOT_EXIST);
        }
        profile.setNickname(trimmed);
        // 管理员改名不重置用户的昵称修改间隔计时
        userProfileMapper.updateById(profile);
        userLogService.write(adminId, "setNickname", "修改用户 " + userId + " 昵称为 " + trimmed);
    }

    // ==================== 内容审核（待审队列） ====================

    public PageVO<AuditMomentVO> listAudits(Long adminId, int page, int size) {
        requireAdmin(adminId);
        int p = Math.max(page, 1);
        int s = Math.min(Math.max(size, 1), 50);

        LambdaQueryWrapper<Moment> wrapper = new LambdaQueryWrapper<Moment>()
                .eq(Moment::getStatus, Moment.STATUS_PENDING)
                .orderByAsc(Moment::getId);
        long total = momentMapper.selectCount(wrapper);
        List<Moment> moments = momentMapper.selectList(wrapper.last("LIMIT " + (p - 1) * s + ", " + s));
        Map<Long, UserProfile> profileMap = profilesOf(moments.stream().map(Moment::getUserId).collect(Collectors.toSet()));
        Map<Long, List<MomentMedia>> mediaMap = mediaOf(moments.stream().map(Moment::getId).collect(Collectors.toSet()));

        List<AuditMomentVO> records = moments.stream().map(m -> {
            AuditMomentVO vo = new AuditMomentVO();
            vo.setId(m.getId());
            vo.setAuthorId(m.getUserId());
            UserProfile author = profileMap.get(m.getUserId());
            vo.setAuthorNickname(author == null ? null : author.getNickname());
            vo.setAuthorAvatar(author == null ? null : author.getAvatar());
            vo.setContent(m.getContent());
            vo.setVisibility(m.getVisibility());
            vo.setMedia(mediaMap.getOrDefault(m.getId(), List.of()).stream().map(this::toMediaVO).toList());
            vo.setCreatedAt(m.getCreatedAt());
            return vo;
        }).collect(Collectors.toList());

        return pageOf(records, p, s, total);
    }

    /** 审核动态：approve 放行 / reject 不通过。 */
    @Transactional
    public void auditMoment(Long adminId, Long momentId, String action) {
        requireAdmin(adminId);
        Moment moment = momentMapper.selectById(momentId);
        if (moment == null || moment.getStatus() != Moment.STATUS_PENDING) {
            throw new BusinessException(ErrorCode.MOMENT_NOT_EXIST, "该动态不在待审核队列");
        }
        if ("approve".equals(action)) {
            moment.setStatus(Moment.STATUS_PUBLISHED);
        } else if ("reject".equals(action)) {
            moment.setStatus(Moment.STATUS_REJECTED);
        } else {
            throw new BusinessException(ErrorCode.PARAM_ERROR, "审核动作不正确");
        }
        momentMapper.updateById(moment);
        userLogService.write(adminId, "audit", action + " 动态 " + momentId);
    }

    // ==================== 举报处理 ====================

    public PageVO<ReportAdminVO> listReports(Long adminId, int page, int size) {
        requireAdmin(adminId);
        int p = Math.max(page, 1);
        int s = Math.min(Math.max(size, 1), 50);

        LambdaQueryWrapper<Report> wrapper = new LambdaQueryWrapper<Report>()
                .eq(Report::getHandleStatus, 0)
                .orderByAsc(Report::getId);
        long total = reportMapper.selectCount(wrapper);
        List<Report> reports = reportMapper.selectList(wrapper.last("LIMIT " + (p - 1) * s + ", " + s));

        Set<Long> momentIds = reports.stream().map(Report::getMomentId).collect(Collectors.toSet());
        Map<Long, Moment> momentMap = momentIds.isEmpty() ? Map.of()
                : momentMapper.selectList(new LambdaQueryWrapper<Moment>().in(Moment::getId, momentIds))
                        .stream().collect(Collectors.toMap(Moment::getId, Function.identity()));
        Set<Long> userIds = new HashSet<>();
        reports.forEach(r -> {
            Moment m = momentMap.get(r.getMomentId());
            if (m != null) {
                userIds.add(m.getUserId());
            }
            userIds.add(r.getReporterId());
        });
        Map<Long, UserProfile> profileMap = profilesOf(userIds);

        List<ReportAdminVO> records = reports.stream().map(r -> {
            ReportAdminVO vo = new ReportAdminVO();
            vo.setId(r.getId());
            vo.setMomentId(r.getMomentId());
            Moment m = momentMap.get(r.getMomentId());
            vo.setMomentContent(m == null ? null : truncate(m.getContent(), 100));
            vo.setMomentAuthorNickname(m == null ? null : nick(profileMap, m.getUserId()));
            vo.setReporterNickname(nick(profileMap, r.getReporterId()));
            vo.setReason(r.getReason());
            vo.setHandleStatus(r.getHandleStatus());
            vo.setCreatedAt(r.getCreatedAt());
            return vo;
        }).collect(Collectors.toList());

        return pageOf(records, p, s, total);
    }

    /** 处理举报：delete 删除动态 / ignore 忽略。 */
    @Transactional
    public void handleReport(Long adminId, Long reportId, String action) {
        requireAdmin(adminId);
        Report report = reportMapper.selectById(reportId);
        if (report == null || Integer.valueOf(1).equals(report.getHandleStatus())) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "举报不存在或已处理");
        }
        if ("delete".equals(action)) {
            Moment moment = momentMapper.selectById(report.getMomentId());
            if (moment != null && moment.getStatus() != Moment.STATUS_DELETED) {
                moment.setStatus(Moment.STATUS_DELETED);
                momentMapper.updateById(moment);
            }
        } else if (!"ignore".equals(action)) {
            throw new BusinessException(ErrorCode.PARAM_ERROR, "处理动作不正确");
        }
        report.setHandleStatus(1);
        report.setHandledBy(adminId);
        report.setHandledAt(LocalDateTime.now(ZoneId.of("Asia/Shanghai")));
        reportMapper.updateById(report);
        userLogService.write(adminId, "reportHandle", action + " 举报 " + reportId);
    }

    // ==================== 操作日志 ====================

    public PageVO<UserLogVO> listLogs(Long adminId, int page, int size) {
        requireAdmin(adminId);
        int p = Math.max(page, 1);
        int s = Math.min(Math.max(size, 1), 50);

        LambdaQueryWrapper<UserLog> wrapper = new LambdaQueryWrapper<UserLog>().orderByDesc(UserLog::getId);
        long total = userLogMapper.selectCount(wrapper);
        List<UserLog> logs = userLogMapper.selectList(wrapper.last("LIMIT " + (p - 1) * s + ", " + s));
        List<UserLogVO> records = logs.stream().map(l -> {
            UserLogVO vo = new UserLogVO();
            vo.setId(l.getId());
            vo.setUserId(l.getUserId());
            vo.setAction(l.getAction());
            vo.setDetail(l.getDetail());
            vo.setIp(l.getIp());
            vo.setCreatedAt(l.getCreatedAt());
            return vo;
        }).collect(Collectors.toList());
        return pageOf(records, p, s, total);
    }

    // ==================== 内部辅助 ====================

    private <T> PageVO<T> pageOf(List<T> records, int page, int size, long total) {
        PageVO<T> vo = new PageVO<>();
        vo.setRecords(records);
        vo.setPage(page);
        vo.setSize(size);
        vo.setTotal(total);
        vo.setHasMore((long) page * size < total);
        return vo;
    }

    private Map<Long, UserProfile> profilesOf(Set<Long> userIds) {
        if (userIds == null || userIds.isEmpty()) {
            return Map.of();
        }
        return userProfileMapper.selectList(new LambdaQueryWrapper<UserProfile>().in(UserProfile::getUserId, userIds))
                .stream().collect(Collectors.toMap(UserProfile::getUserId, Function.identity()));
    }

    private Map<Long, List<MomentMedia>> mediaOf(Set<Long> momentIds) {
        if (momentIds == null || momentIds.isEmpty()) {
            return Map.of();
        }
        return momentMediaMapper.selectList(new LambdaQueryWrapper<MomentMedia>()
                        .in(MomentMedia::getMomentId, momentIds)
                        .orderByAsc(MomentMedia::getSort))
                .stream().collect(Collectors.groupingBy(MomentMedia::getMomentId));
    }

    private MomentMediaVO toMediaVO(MomentMedia media) {
        MomentMediaVO vo = new MomentMediaVO();
        vo.setId(media.getId());
        vo.setMediaType(media.getMediaType());
        vo.setUrl(media.getUrl());
        vo.setThumbnailUrl(media.getThumbnailUrl());
        vo.setWidth(media.getWidth());
        vo.setHeight(media.getHeight());
        vo.setSort(media.getSort());
        return vo;
    }

    private String nick(Map<Long, UserProfile> profileMap, Long userId) {
        UserProfile profile = profileMap.get(userId);
        return profile == null ? null : profile.getNickname();
    }

    private String maskPhone(String phone) {
        if (phone == null || phone.length() < 7) {
            return phone;
        }
        return phone.substring(0, 3) + "****" + phone.substring(7);
    }

    /** 邮箱脱敏：保留前两位 + @域名，如 al***@example.com。 */
    private String maskEmail(String email) {
        if (email == null || !email.contains("@")) {
            return email;
        }
        int at = email.indexOf('@');
        String local = email.substring(0, at);
        if (local.length() <= 2) {
            return local.charAt(0) + "***" + email.substring(at);
        }
        return local.substring(0, 2) + "***" + email.substring(at);
    }

    private String truncate(String s, int max) {
        if (s == null || s.length() <= max) {
            return s;
        }
        return s.substring(0, max);
    }

    private String piiKey() {
        return properties.getPii().getKey();
    }
}
