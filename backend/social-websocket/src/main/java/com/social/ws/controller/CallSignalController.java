package com.social.ws.controller;

import com.social.common.exception.BusinessException;
import com.social.service.dto.CallSignalVO;
import com.social.service.dto.UserVO;
import com.social.service.service.CallService;
import com.social.service.service.UserService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Controller;

import java.security.Principal;
import java.util.UUID;

/**
 * 通话信令（STOMP /app/call）。
 *
 * <p>@MessageMapping 走消息线程，无 HTTP 上下文 —— 用户身份取 {@link Principal}，
 * 不能用 UserContext。服务端负责：呼叫生命周期状态机（CallService）与信令透传。
 * 校验失败以 busy / offline 回执给发送方，不抛异常（消息处理器无全局异常兜底）。
 */
@Slf4j
@Controller
@RequiredArgsConstructor
public class CallSignalController {

    private final CallService callService;
    private final UserService userService;
    private final SimpMessagingTemplate messaging;

    @MessageMapping("/call")
    public void onSignal(@Payload CallSignalVO sig, Principal principal) {
        if (sig == null || sig.getAction() == null || principal == null) {
            return;
        }
        Long self = Long.valueOf(principal.getName());
        try {
            dispatch(self, sig);
        } catch (BusinessException e) {
            log.warn("通话信令被拒 user={} action={} callId={} -> {}", self, sig.getAction(), sig.getCallId(), e.getMessage());
            rejectFailure(self, sig, e);
        } catch (Exception e) {
            log.error("通话信令异常 user={} action={} callId={}", self, sig.getAction(), sig.getCallId(), e);
        }
    }

    private void dispatch(Long self, CallSignalVO sig) {
        switch (sig.getAction()) {
            case CallSignalVO.ACTION_INVITE -> invite(self, sig);
            case CallSignalVO.ACTION_ACCEPT -> accept(self, sig);
            case CallSignalVO.ACTION_REJECT -> reject(self, sig);
            case CallSignalVO.ACTION_CANCEL -> cancel(self, sig);
            case CallSignalVO.ACTION_OFFER -> offer(self, sig);
            case CallSignalVO.ACTION_ANSWER -> answer(self, sig);
            case CallSignalVO.ACTION_CANDIDATE -> candidate(self, sig);
            case CallSignalVO.ACTION_END -> end(self, sig);
            default -> log.warn("未知通话信令 action={}", sig.getAction());
        }
    }

    private void invite(Long callerId, CallSignalVO sig) {
        Long calleeId = sig.getTo() != null ? sig.getTo() : sig.getCalleeId();
        String callId = (sig.getCallId() == null || sig.getCallId().isBlank())
                ? UUID.randomUUID().toString().replace("-", "") : sig.getCallId();
        callService.createCall(callId, callerId, calleeId, sig.getCallType());

        CallSignalVO toCallee = base(sig, CallSignalVO.ACTION_INVITE);
        toCallee.setCallId(callId);
        toCallee.setCallType(sig.getCallType());
        toCallee.setCallerId(callerId);
        toCallee.setCalleeId(calleeId);
        // 回填主叫信息供来电页展示
        fillPeer(toCallee, callerId);
        push(calleeId, toCallee);

        // 注意：不向主叫回执 invite——前端以本地生成的 callId 为准，回执会触发
        // 前端 handleInvite 的忙碌防御逻辑（主叫状态非 idle）导致误拒接，显示"对方拒听"。
        log.info("邀请已转发 callId={} caller={} callee={} type={}", callId, callerId, calleeId, sig.getCallType());
    }

    private void accept(Long calleeId, CallSignalVO sig) {
        callService.accept(sig.getCallId());
        CallSignalVO toCaller = base(sig, CallSignalVO.ACTION_ACCEPT);
        push(callService.get(sig.getCallId()).getCallerId(), toCaller);
    }

    private void reject(Long calleeId, CallSignalVO sig) {
        Long callerId = callService.get(sig.getCallId()).getCallerId();
        callService.reject(sig.getCallId());
        CallSignalVO toCaller = base(sig, CallSignalVO.ACTION_REJECT);
        push(callerId, toCaller);
    }

    private void cancel(Long callerId, CallSignalVO sig) {
        CallService.ActiveCall call = callService.get(sig.getCallId());
        callService.cancel(sig.getCallId());
        CallSignalVO toCallee = base(sig, CallSignalVO.ACTION_CANCEL);
        push(call.getCalleeId(), toCallee);
    }

    /** WebRTC 协商：SDP / ICE 透传。 */
    private void offer(Long callerId, CallSignalVO sig) {
        CallSignalVO toCallee = base(sig, CallSignalVO.ACTION_OFFER);
        toCallee.setSdp(sig.getSdp());
        push(sig.getTo(), toCallee);
    }

    private void answer(Long calleeId, CallSignalVO sig) {
        CallSignalVO toCaller = base(sig, CallSignalVO.ACTION_ANSWER);
        toCaller.setSdp(sig.getSdp());
        push(sig.getTo(), toCaller);
    }

    private void candidate(Long self, CallSignalVO sig) {
        CallSignalVO toPeer = base(sig, CallSignalVO.ACTION_CANDIDATE);
        toPeer.setCandidate(sig.getCandidate());
        push(sig.getTo(), toPeer);
    }

    private void end(Long self, CallSignalVO sig) {
        Long peerId;
        try {
            peerId = callService.end(sig.getCallId(), sig.getReason(), self);
        } catch (Exception e) {
            peerId = sig.getTo(); // 通话已结束：仍尽量通知对端
        }
        if (peerId == null) {
            return;
        }
        CallSignalVO toPeer = base(sig, CallSignalVO.ACTION_END);
        toPeer.setReason(sig.getReason());
        push(peerId, toPeer);
    }

    /** 失败回执（busy / offline）发给发送方。 */
    private void rejectFailure(Long self, CallSignalVO sig, BusinessException e) {
        CallSignalVO back = base(sig, e.getCode() == 4005 ? CallSignalVO.ACTION_OFFLINE : CallSignalVO.ACTION_BUSY);
        back.setReason(e.getMessage());
        push(self, back);
    }

    private void fillPeer(CallSignalVO sig, Long userId) {
        try {
            UserVO u = userService.getUserVO(userId);
            sig.setPeerName(u.getNickname());
            sig.setPeerAvatar(u.getAvatar());
        } catch (Exception ignored) {
        }
    }

    private CallSignalVO base(CallSignalVO sig, String action) {
        CallSignalVO vo = new CallSignalVO();
        vo.setAction(action);
        vo.setCallId(sig.getCallId());
        vo.setCallType(sig.getCallType());
        return vo;
    }

    private void push(Long userId, CallSignalVO sig) {
        try {
            messaging.convertAndSendToUser(String.valueOf(userId), "/queue/call", sig);
        } catch (Exception e) {
            log.warn("通话信令推送失败 userId={} action={}", userId, sig.getAction(), e);
        }
    }
}
