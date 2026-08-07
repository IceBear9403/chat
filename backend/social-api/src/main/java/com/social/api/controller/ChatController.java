package com.social.api.controller;

import com.social.api.dto.request.OpenConversationRequest;
import com.social.api.dto.request.SendMessageRequest;
import com.social.common.context.UserContext;
import com.social.common.model.Result;
import com.social.service.dto.ChatHistoryVO;
import com.social.service.dto.ConversationVO;
import com.social.service.dto.MessageVO;
import com.social.service.entity.Message;
import com.social.service.service.ChatService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 即时聊天接口。需要登录（JWT）。
 */
@Tag(name = "聊天", description = "会话 / 消息 / 已读")
@RestController
@RequestMapping("/api/chat")
@RequiredArgsConstructor
public class ChatController {

    private final ChatService chatService;

    @Operation(summary = "发送消息")
    @PostMapping("/send")
    public Result<MessageVO> send(@Valid @RequestBody SendMessageRequest request) {
        Message message = new Message();
        message.setReceiverId(request.getReceiverId());
        message.setMsgType(request.getMsgType());
        message.setContent(request.getContent());
        message.setClientMsgId(request.getClientMsgId());
        return Result.ok(chatService.sendMessage(UserContext.getUserId(), message));
    }

    @Operation(summary = "打开会话（获取或创建，返回会话详情）")
    @PostMapping("/conversation")
    public Result<ConversationVO> openConversation(@Valid @RequestBody OpenConversationRequest request) {
        return Result.ok(chatService.openConversation(UserContext.getUserId(), request.getPeerId()));
    }

    @Operation(summary = "会话列表（含未读、在线状态）")
    @GetMapping("/conversations")
    public Result<List<ConversationVO>> conversations() {
        return Result.ok(chatService.conversationList(UserContext.getUserId()));
    }

    @Operation(summary = "会话详情")
    @GetMapping("/{conversationId}")
    public Result<ConversationVO> conversationDetail(@PathVariable Long conversationId) {
        return Result.ok(chatService.conversationDetail(UserContext.getUserId(), conversationId));
    }

    @Operation(summary = "会话历史（游标分页，升序时间线）")
    @GetMapping("/{conversationId}/messages")
    public Result<ChatHistoryVO> messages(@PathVariable Long conversationId,
                                          @RequestParam(required = false) Long cursorId,
                                          @RequestParam(defaultValue = "20") int size) {
        return Result.ok(chatService.messageList(UserContext.getUserId(), conversationId, cursorId, size));
    }

    @Operation(summary = "标记会话已读（推送已读回执给对端）")
    @PostMapping("/{conversationId}/read")
    public Result<Void> read(@PathVariable Long conversationId) {
        chatService.markRead(UserContext.getUserId(), conversationId);
        return Result.ok();
    }
}
