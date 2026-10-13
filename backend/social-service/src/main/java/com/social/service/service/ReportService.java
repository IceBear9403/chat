package com.social.service.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.social.service.entity.Report;
import com.social.service.mapper.ReportMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/**
 * 举报服务：用户举报落库（同一用户对同一动态仅保留一条待处理举报），管理端处理见 AdminService。
 */
@Service
@RequiredArgsConstructor
public class ReportService {

    private final ReportMapper reportMapper;
    private final UserLogService userLogService;

    /** 举报动态。重复举报同一动态（待处理）时静默忽略。 */
    public void report(Long userId, Long momentId, String reason) {
        Long exists = reportMapper.selectCount(new LambdaQueryWrapper<Report>()
                .eq(Report::getMomentId, momentId)
                .eq(Report::getReporterId, userId)
                .eq(Report::getHandleStatus, 0));
        if (exists != null && exists > 0) {
            return;
        }
        Report report = new Report();
        report.setMomentId(momentId);
        report.setReporterId(userId);
        report.setReason(reason == null ? null : reason.trim());
        report.setHandleStatus(0);
        reportMapper.insert(report);
        userLogService.write(userId, "report", "举报动态 " + momentId);
    }
}
