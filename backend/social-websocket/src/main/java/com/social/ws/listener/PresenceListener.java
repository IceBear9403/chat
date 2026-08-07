package com.social.ws.listener;

import com.social.service.dto.CallSignalVO;
import com.social.service.service.CallService;
import com.social.service.service.PresenceService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.event.EventListener;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.messaging.SessionConnectedEvent;
import org.springframework.web.socket.messaging.SessionDisconnectEvent;

import java.security.Principal;

/**
 * STOMP 连接/断开 → Redis 在线状态 + 通话掉线处理。
 *
 * <p>多端场景简化处理：任一会话断开即标记离线（阶段三精度够用）。
 * 通话中断线：其进行中的通话落库标记掉线（status 5），并向对端推送 end。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class PresenceListener {

    private final PresenceService presenceService;
    private final CallService callService;
    private final SimpMessagingTemplate messaging;

    @EventListener
    public void onConnected(SessionConnectedEvent event) {
        mark(event.getUser(), true);
    }

    @EventListener
    public void onDisconnected(SessionDisconnectEvent event) {
        Principal user = event.getUser();
        mark(user, false);
        dropCall(user);
    }

    /** 断线掉话：通知对端通话已结束。 */
    private void dropCall(Principal user) {
        if (user == null) {
            return;
        }
        try {
            Long userId = Long.valueOf(user.getName());
            Long peerId = callService.dropUserCall(userId);
            if (peerId != null) {
                CallSignalVO end = new CallSignalVO();
                end.setAction(CallSignalVO.ACTION_END);
                end.setReason("offline");
                try {
                    messaging.convertAndSendToUser(String.valueOf(peerId), "/queue/call", end);
                } catch (Exception e) {
                    log.warn("掉线通知失败 peer={}", peerId, e);
                }
            }
        } catch (NumberFormatException e) {
            log.warn("Presence principal 非数字 userId: {}", user.getName());
        }
    }

    private void mark(Principal user, boolean online) {
        if (user == null) {
            return;
        }
        try {
            Long userId = Long.valueOf(user.getName());
            if (online) {
                presenceService.markOnline(userId);
            } else {
                presenceService.markOffline(userId);
            }
        } catch (NumberFormatException e) {
            log.warn("Presence principal 非数字 userId: {}", user.getName());
        }
    }
}
