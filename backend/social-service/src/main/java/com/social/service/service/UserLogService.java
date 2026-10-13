package com.social.service.service;

import com.social.common.context.UserContext;
import com.social.service.entity.UserLog;
import com.social.service.mapper.UserLogMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/**
 * 操作日志记录（阶段七合规：登录/关键操作留痕，日志留存 ≥180 天）。
 * IP / UA 从 {@link UserContext} 读取（由 social-api 的 RequestIpFilter 写入），
 * service 层不依赖 servlet API。写入失败不影响主流程。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class UserLogService {

    private final UserLogMapper userLogMapper;

    /** 记录一条操作日志（@Transactional 内参与事务；失败吞掉，不阻断主流程）。 */
    public void write(Long userId, String action, String detail) {
        try {
            UserLog record = new UserLog();
            record.setUserId(userId);
            record.setAction(action);
            record.setDetail(truncate(detail, 500));
            record.setIp(truncate(UserContext.getClientIp(), 50));
            record.setUserAgent(truncate(UserContext.getUserAgent(), 255));
            userLogMapper.insert(record);
        } catch (Exception e) {
            log.warn("操作日志写入失败：action={} err={}", action, e.getMessage());
        }
    }

    private String truncate(String s, int max) {
        if (s == null || s.length() <= max) {
            return s;
        }
        return s.substring(0, max);
    }
}
