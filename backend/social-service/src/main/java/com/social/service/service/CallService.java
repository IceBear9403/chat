package com.social.service.service;

import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.social.common.enums.ErrorCode;
import com.social.common.exception.BusinessException;
import com.social.service.entity.CallRecord;
import com.social.service.mapper.CallRecordMapper;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 通话状态管理 + 通话记录持久化。
 *
 * <p>活跃通话在内存中（单实例够用）；call_record 落库做历史记录。
 * busy / 离线判断依赖在线状态（Redis presence）与进行中通话表。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class CallService {

    private final CallRecordMapper callRecordMapper;
    private final PresenceService presenceService;

    /** callId -> 活跃通话 */
    private final ConcurrentHashMap<String, ActiveCall> calls = new ConcurrentHashMap<>();
    /** userId -> callId（用于忙碌判断） */
    private final ConcurrentHashMap<Long, String> userCall = new ConcurrentHashMap<>();

    /** 创建呼叫（invite）。对端忙碌/离线抛业务异常，由信令层回执 busy/offline。 */
    public ActiveCall createCall(String callId, Long callerId, Long calleeId, Integer callType) {
        if (callerId.equals(calleeId)) {
            throw new BusinessException(ErrorCode.PARAM_ERROR, "不能拨打给自己");
        }
        if (callType == null || (callType != CallRecord.TYPE_VOICE && callType != CallRecord.TYPE_VIDEO)) {
            throw new BusinessException(ErrorCode.PARAM_ERROR, "通话类型不正确");
        }
        if (userCall.containsKey(callerId)) {
            throw new BusinessException(ErrorCode.CALL_BUSY, "你正在通话中");
        }
        if (userCall.containsKey(calleeId)) {
            throw new BusinessException(ErrorCode.CALL_BUSY);
        }
        if (!presenceService.online(calleeId)) {
            throw new BusinessException(ErrorCode.CALL_CALLEE_OFFLINE);
        }

        ActiveCall call = new ActiveCall(callId, callerId, calleeId, callType);
        calls.put(callId, call);
        userCall.put(callerId, callId);
        userCall.put(calleeId, callId);

        CallRecord record = new CallRecord();
        record.setCallId(callId);
        record.setCallerId(callerId);
        record.setCalleeId(calleeId);
        record.setCallType(callType);
        record.setStatus(CallRecord.STATUS_RINGING);
        record.setDuration(0);
        callRecordMapper.insert(record);
        log.info("通话发起 callId={} caller={} callee={} type={}", callId, callerId, calleeId, callType);
        return call;
    }

    public ActiveCall get(String callId) {
        ActiveCall call = calls.get(callId);
        if (call == null) {
            throw new BusinessException(ErrorCode.CALL_NOT_EXIST);
        }
        return call;
    }

    /** 接听：状态 1 + started_at。 */
    public ActiveCall accept(String callId) {
        ActiveCall call = get(callId);
        call.setStatus(CallRecord.STATUS_ACTIVE);
        call.setStartedAt(LocalDateTime.now());
        callRecordMapper.update(null, new LambdaUpdateWrapper<CallRecord>()
                .eq(CallRecord::getCallId, callId)
                .set(CallRecord::getStatus, CallRecord.STATUS_ACTIVE)
                .set(CallRecord::getStartedAt, LocalDateTime.now()));
        return call;
    }

    /** 拒绝：状态 2。 */
    public void reject(String callId) {
        ActiveCall call = calls.get(callId);
        if (call != null && call.getStatus() == CallRecord.STATUS_RINGING) {
            callRecordMapper.update(null, new LambdaUpdateWrapper<CallRecord>()
                    .eq(CallRecord::getCallId, callId)
                    .set(CallRecord::getStatus, CallRecord.STATUS_REJECTED)
                    .set(CallRecord::getEndedAt, LocalDateTime.now()));
        }
        cleanup(callId);
    }

    /** 主叫取消（未接通）：状态 3 未接。 */
    public void cancel(String callId) {
        ActiveCall call = calls.get(callId);
        if (call != null && call.getStatus() == CallRecord.STATUS_RINGING) {
            callRecordMapper.update(null, new LambdaUpdateWrapper<CallRecord>()
                    .eq(CallRecord::getCallId, callId)
                    .set(CallRecord::getStatus, CallRecord.STATUS_MISSED)
                    .set(CallRecord::getEndedAt, LocalDateTime.now()));
        }
        cleanup(callId);
    }

    /** 挂断：状态 4，记录时长。返回对端 userId 供信令层推送。 */
    public Long end(String callId, String reason, Long userId) {
        ActiveCall call = calls.get(callId);
        Long peerId = null;
        if (call != null) {
            peerId = call.getCallerId().equals(userId) ? call.getCalleeId() : call.getCallerId();
            LocalDateTime ended = LocalDateTime.now();
            int duration = call.getStartedAt() == null
                    ? 0 : (int) Duration.between(call.getStartedAt(), ended).getSeconds();
            int status = "offline".equals(reason) ? CallRecord.STATUS_DROPPED : CallRecord.STATUS_HANGUP;
            callRecordMapper.update(null, new LambdaUpdateWrapper<CallRecord>()
                    .eq(CallRecord::getCallId, callId)
                    .set(CallRecord::getStatus, status)
                    .set(CallRecord::getDuration, duration)
                    .set(CallRecord::getEndedAt, ended));
            log.info("通话结束 callId={} status={} duration={}s", callId, status, duration);
        }
        cleanup(callId);
        return peerId;
    }

    /** 用户断线：其进行中的通话标记掉线（状态 5），返回对端 userId 供推送，无则返回 null。 */
    public Long dropUserCall(Long userId) {
        String callId = userCall.get(userId);
        if (callId == null) {
            return null;
        }
        ActiveCall call = calls.get(callId);
        Long peerId = null;
        if (call != null) {
            peerId = call.getCallerId().equals(userId) ? call.getCalleeId() : call.getCallerId();
            LocalDateTime ended = LocalDateTime.now();
            int duration = call.getStartedAt() == null
                    ? 0 : (int) Duration.between(call.getStartedAt(), ended).getSeconds();
            callRecordMapper.update(null, new LambdaUpdateWrapper<CallRecord>()
                    .eq(CallRecord::getCallId, callId)
                    .set(CallRecord::getStatus, CallRecord.STATUS_DROPPED)
                    .set(CallRecord::getDuration, duration)
                    .set(CallRecord::getEndedAt, ended));
            log.info("通话掉线 callId={} user={}", callId, userId);
        }
        cleanup(callId);
        return peerId;
    }

    public boolean isInCall(Long userId) {
        return userCall.containsKey(userId);
    }

    private void cleanup(String callId) {
        ActiveCall call = calls.remove(callId);
        if (call != null) {
            userCall.remove(call.getCallerId(), callId);
            userCall.remove(call.getCalleeId(), callId);
        }
    }

    /** 内存中的活跃通话。 */
    @Data
    public static class ActiveCall {
        private final String callId;
        private final Long callerId;
        private final Long calleeId;
        private final Integer callType;
        private volatile int status;
        private volatile LocalDateTime startedAt;

        public ActiveCall(String callId, Long callerId, Long calleeId, Integer callType) {
            this.callId = callId;
            this.callerId = callerId;
            this.calleeId = calleeId;
            this.callType = callType;
            this.status = CallRecord.STATUS_RINGING;
        }
    }
}
