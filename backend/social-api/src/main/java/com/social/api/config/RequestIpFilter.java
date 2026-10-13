package com.social.api.config;

import com.social.common.context.UserContext;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

/**
 * 全量请求 IP / UA 捕获过滤器（阶段七合规日志）。
 *
 * <p>在所有拦截器之前执行，把来源 IP 与 User-Agent 写入 {@link UserContext}，
 * 供 {@code UserLogService} 使用。即便后续认证失败，登录尝试也有据可查。
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class RequestIpFilter extends OncePerRequestFilter {

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        try {
            UserContext.setClientIp(clientIp(request));
            UserContext.setUserAgent(request.getHeader("User-Agent"));
            filterChain.doFilter(request, response);
        } finally {
            // UserContext 由 AuthInterceptor 清理，此处仅兜底（避免异常路径残留）
            UserContext.clear();
        }
    }

    private String clientIp(HttpServletRequest request) {
        String xff = request.getHeader("X-Forwarded-For");
        if (xff != null && !xff.isBlank()) {
            return xff.split(",")[0].trim();
        }
        return request.getRemoteAddr();
    }
}
