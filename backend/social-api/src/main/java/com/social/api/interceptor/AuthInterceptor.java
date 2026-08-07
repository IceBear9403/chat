package com.social.api.interceptor;

import com.social.common.context.UserContext;
import com.social.common.enums.ErrorCode;
import com.social.common.exception.BusinessException;
import com.social.common.util.JwtUtil;
import com.social.service.config.AppProperties;
import io.jsonwebtoken.Claims;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

/**
 * JWT 认证拦截器。
 *
 * <p>校验 {@code Authorization: Bearer <accessToken>}，通过后把 userId 写入 {@link UserContext}，
 * 请求结束由 {@link #afterCompletion} 清理（防线程池串号）。
 */
@Component
@RequiredArgsConstructor
public class AuthInterceptor implements HandlerInterceptor {

    public static final String TOKEN_PREFIX = "Bearer ";

    private final AppProperties properties;

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        String header = request.getHeader(HttpHeaders.AUTHORIZATION);
        if (header == null || !header.startsWith(TOKEN_PREFIX)) {
            throw new BusinessException(ErrorCode.UNAUTHORIZED, "请先登录");
        }
        String token = header.substring(TOKEN_PREFIX.length()).trim();
        try {
            Claims claims = JwtUtil.parse(token, properties.getJwt().getAccessSecret());
            UserContext.setUserId(JwtUtil.getUserId(claims));
        } catch (io.jsonwebtoken.ExpiredJwtException e) {
            throw new BusinessException(ErrorCode.TOKEN_EXPIRED, "登录已过期，请重新登录");
        } catch (Exception e) {
            throw new BusinessException(ErrorCode.TOKEN_INVALID, "无效的登录令牌");
        }
        return true;
    }

    @Override
    public void afterCompletion(HttpServletRequest request, HttpServletResponse response, Object handler, Exception ex) {
        UserContext.clear();
    }
}
