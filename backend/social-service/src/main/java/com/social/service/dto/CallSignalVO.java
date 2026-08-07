package com.social.service.dto;

import lombok.Data;

/**
 * 通话信令（/app/call 收发，/user/{id}/queue/call 推送）。
 *
 * <p>action：
 * <ul>
 *   <li>invite / accept / reject / cancel：呼叫生命周期（取消=未接）</li>
 *   <li>offer / answer / candidate：WebRTC 协商，服务端透传</li>
 *   <li>end：挂断 / 掉线；busy / offline：失败回执</li>
 * </ul>
 */
@Data
public class CallSignalVO {

    public static final String ACTION_INVITE = "invite";
    public static final String ACTION_ACCEPT = "accept";
    public static final String ACTION_REJECT = "reject";
    public static final String ACTION_CANCEL = "cancel";
    public static final String ACTION_OFFER = "offer";
    public static final String ACTION_ANSWER = "answer";
    public static final String ACTION_CANDIDATE = "candidate";
    public static final String ACTION_END = "end";
    public static final String ACTION_BUSY = "busy";
    public static final String ACTION_OFFLINE = "offline";

    private String action;

    /** 全局呼叫 ID */
    private String callId;

    /** 1 语音，2 视频 */
    private Integer callType;

    private Long callerId;
    private Long calleeId;

    /** 发送方 userId（透传时服务端回填） */
    private Long from;

    /** 接收方 userId */
    private Long to;

    /** offer / answer 的 SDP */
    private String sdp;

    /** ICE candidate（JSON 字符串） */
    private String candidate;

    /** reject / end 原因 */
    private String reason;

    /** 对方昵称 / 头像（invite 时回填主叫信息供来电页展示） */
    private String peerName;
    private String peerAvatar;
}
