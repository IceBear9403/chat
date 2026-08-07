package com.social.ws.config;

import com.social.ws.auth.StompAuthHandshakeInterceptor;
import com.social.ws.auth.UserIdHandshakeHandler;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Configuration;
import org.springframework.messaging.simp.config.MessageBrokerRegistry;
import org.springframework.web.socket.config.annotation.EnableWebSocketMessageBroker;
import org.springframework.web.socket.config.annotation.StompEndpointRegistry;
import org.springframework.web.socket.config.annotation.WebSocketMessageBrokerConfigurer;

/**
 * WebSocket/STOMP 配置。
 *
 * <ul>
 *   <li>端点 {@code /ws}（原生 WebSocket），握手时做 JWT 鉴权，浏览器无法自定义 Header，
 *       token 走查询参数 {@code ?token=xxx}</li>
 *   <li>客户端发消息到 {@code /app/**}，订阅推送在 {@code /topic/**} 与 {@code /user/queue/**}</li>
 * </ul>
 *
 * 生产走 Nginx WSS 反代（阶段六）。保留原 SockJS 端点 {@code /ws-sockjs} 供网络受限环境降级。
 */
@Configuration
@EnableWebSocketMessageBroker
@RequiredArgsConstructor
public class WebSocketConfig implements WebSocketMessageBrokerConfigurer {

    private final StompAuthHandshakeInterceptor authHandshakeInterceptor;
    private final UserIdHandshakeHandler userIdHandshakeHandler;

    @Override
    public void registerStompEndpoints(StompEndpointRegistry registry) {
        registry.addEndpoint("/ws")
                .addInterceptors(authHandshakeInterceptor)
                .setHandshakeHandler(userIdHandshakeHandler)
                .setAllowedOriginPatterns("*");
        // SockJS 降级端点（轮询/长连接兜底，前端未默认使用）
        registry.addEndpoint("/ws-sockjs")
                .addInterceptors(authHandshakeInterceptor)
                .setHandshakeHandler(userIdHandshakeHandler)
                .setAllowedOriginPatterns("*")
                .withSockJS();
    }

    @Override
    public void configureMessageBroker(MessageBrokerRegistry registry) {
        // 广播 / 定向推送
        registry.enableSimpleBroker("/topic", "/queue");
        // 客户端 @MessageMapping 前缀
        registry.setApplicationDestinationPrefixes("/app");
        // 用户定向消息 /user/queue/xxx
        registry.setUserDestinationPrefix("/user");
    }
}
