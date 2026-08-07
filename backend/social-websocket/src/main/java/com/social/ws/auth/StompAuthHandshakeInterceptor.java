package com.social.ws.auth;

import com.social.common.util.JwtUtil;
import com.social.service.config.AppProperties;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.server.ServerHttpRequest;
import org.springframework.http.server.ServerHttpResponse;
import org.springframework.lang.NonNull;
import org.springframework.stereotype.Component;
import org.springframework.util.MultiValueMap;
import org.springframework.web.socket.WebSocketHandler;
import org.springframework.web.socket.server.HandshakeInterceptor;
import org.springframework.web.util.UriComponentsBuilder;

import java.util.Map;

/**
 * WebSocket 握手鉴权。
 *
 * <p>浏览器 WebSocket 无法自定义 Authorization 头，token 通过查询参数 {@code ?token=xxx} 传递
 * （SockJS 握手路径为 /ws/{id}，需从前缀后续参数中取）。校验失败直接拒绝握手。
 */
@Component
@RequiredArgsConstructor
public class StompAuthHandshakeInterceptor implements HandshakeInterceptor {

    private final AppProperties properties;

    @Override
    public boolean beforeHandshake(@NonNull ServerHttpRequest request,
                                   @NonNull ServerHttpResponse response,
                                   @NonNull WebSocketHandler wsHandler,
                                   @NonNull Map<String, Object> attributes) {
        String token = extractToken(request);
        if (token == null) {
            return false;
        }
        try {
            JwtUtil.parse(token, properties.getJwt().getAccessSecret());
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    @Override
    public void afterHandshake(@NonNull ServerHttpRequest request,
                               @NonNull ServerHttpResponse response,
                               @NonNull WebSocketHandler wsHandler,
                               Exception exception) {
        // 无需处理
    }

    private String extractToken(ServerHttpRequest request) {
        // 优先从 Authorization 头取（原生 WebSocket 场景）
        HttpHeaders headers = request.getHeaders();
        String header = headers.getFirst(HttpHeaders.AUTHORIZATION);
        if (header != null && header.startsWith("Bearer ")) {
            return header.substring(7);
        }
        // SockJS 场景：token 放在连接 URL 的查询参数中
        MultiValueMap<String, String> params = UriComponentsBuilder.fromUri(request.getURI()).build().getQueryParams();
        String token = params.getFirst("token");
        if (token != null && !token.isBlank()) {
            return token;
        }
        return null;
    }
}
