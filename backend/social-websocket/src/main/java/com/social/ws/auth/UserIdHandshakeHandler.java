package com.social.ws.auth;

import com.social.common.util.JwtUtil;
import com.social.service.config.AppProperties;
import io.jsonwebtoken.Claims;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.server.ServerHttpRequest;
import org.springframework.lang.NonNull;
import org.springframework.stereotype.Component;
import org.springframework.util.MultiValueMap;
import org.springframework.web.socket.WebSocketHandler;
import org.springframework.web.socket.server.support.DefaultHandshakeHandler;
import org.springframework.web.util.UriComponentsBuilder;

import java.security.Principal;
import java.util.Map;

/**
 * 把校验通过的 userId 绑定为 STOMP Principal。
 *
 * <p>这样 {@code @MessageMapping} 中可注入 {@link Principal} 拿到当前用户，且
 * {@code @SendToUser} 与 {@code /user/queue/...} 定向推送可正常工作（阶段三依赖）。
 */
@Component
@RequiredArgsConstructor
public class UserIdHandshakeHandler extends DefaultHandshakeHandler {

    private final AppProperties properties;

    @Override
    protected Principal determineUser(@NonNull ServerHttpRequest request,
                                      @NonNull WebSocketHandler wsHandler,
                                      @NonNull Map<String, Object> attributes) {
        String token = extractToken(request);
        if (token == null) {
            return null;
        }
        try {
            Claims claims = JwtUtil.parse(token, properties.getJwt().getAccessSecret());
            Long userId = JwtUtil.getUserId(claims);
            return () -> String.valueOf(userId);
        } catch (Exception e) {
            return null;
        }
    }

    private String extractToken(ServerHttpRequest request) {
        HttpHeaders headers = request.getHeaders();
        String header = headers.getFirst(HttpHeaders.AUTHORIZATION);
        if (header != null && header.startsWith("Bearer ")) {
            return header.substring(7);
        }
        MultiValueMap<String, String> params = UriComponentsBuilder.fromUri(request.getURI()).build().getQueryParams();
        String token = params.getFirst("token");
        return (token != null && !token.isBlank()) ? token : null;
    }
}
