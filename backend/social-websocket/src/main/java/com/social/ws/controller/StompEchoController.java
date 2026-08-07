package com.social.ws.controller;

import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.messaging.simp.annotation.SendToUser;
import org.springframework.stereotype.Controller;

import java.security.Principal;

/**
 * WebSocket 连通性测试（阶段一）：客户端发送到 /app/echo，服务端原样回显到 /user/queue/echo。
 * 阶段三正式聊天实现后保留用于链路自检。
 */
@Controller
public class StompEchoController {

    @MessageMapping("/echo")
    @SendToUser("/queue/echo")
    public String echo(@Payload String payload, Principal principal) {
        return "echo[" + principal.getName() + "]: " + payload;
    }
}
