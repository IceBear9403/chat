package com.social.common.enums;

import lombok.Getter;

/**
 * 统一业务错误码。
 *
 * <p>约定：
 * <ul>
 *   <li>0          —— 成功</li>
 *   <li>1xxx       —— 通用错误（参数、系统）</li>
 *   <li>2xxx       —— 认证 / 账号</li>
 *   <li>3xxx       —— 朋友圈 / 内容</li>
 *   <li>4xxx       —— 聊天 / 通话</li>
 * </ul>
 */
@Getter
public enum ErrorCode {

    SUCCESS(0, "成功"),

    // ---------- 通用 1xxx ----------
    PARAM_ERROR(1001, "参数错误"),
    UNAUTHORIZED(1002, "未登录或登录已过期"),
    FORBIDDEN(1003, "没有操作权限"),
    NOT_FOUND(1004, "资源不存在"),
    SYSTEM_ERROR(1005, "系统繁忙，请稍后重试"),
    RATE_LIMITED(1006, "操作过于频繁，请稍后再试"),
    FILE_UPLOAD_ERROR(1007, "文件上传失败"),
    FILE_SIZE_EXCEEDED(1008, "文件大小超出限制"),

    // ---------- 认证 / 账号 2xxx ----------
    VERIFY_CODE_SEND_FAILED(2001, "验证码发送失败"),
    VERIFY_CODE_INVALID(2002, "验证码错误或已过期"),
    PHONE_ALREADY_REGISTERED(2003, "该手机号已注册"),
    EMAIL_ALREADY_REGISTERED(2011, "该邮箱已注册"),
    MAIL_SEND_FAILED(2012, "验证码邮件发送失败"),
    USER_NOT_EXIST(2004, "用户不存在"),
    PASSWORD_ERROR(2005, "手机号或密码错误"),
    TOKEN_INVALID(2006, "令牌无效"),
    TOKEN_EXPIRED(2007, "令牌已过期"),
    REFRESH_TOKEN_INVALID(2008, "刷新令牌无效，请重新登录"),
    NICKNAME_ILLEGAL(2009, "昵称包含非法字符"),
    NICKNAME_CHANGE_TOO_FREQUENT(2013, "昵称修改过于频繁"),
    USER_STATUS_FORBIDDEN(2010, "账号已被封禁"),

    // ---------- 朋友圈 / 内容 3xxx ----------
    MOMENT_NOT_EXIST(3001, "动态不存在"),
    MOMENT_ALREADY_LIKED(3002, "已点赞"),
    MOMENT_NOT_LIKED(3003, "尚未点赞"),
    MOMENT_CONTENT_EMPTY(3004, "动态内容不能为空"),
    MOMENT_AUDIT_FAILED(3005, "内容审核未通过"),

    // ---------- 聊天 / 通话 4xxx ----------
    CONVERSATION_NOT_EXIST(4001, "会话不存在"),
    MESSAGE_SEND_FAILED(4002, "消息发送失败"),
    CALL_BUSY(4003, "对方正在通话中"),
    CALL_TIMEOUT(4004, "呼叫超时"),
    CALL_CALLEE_OFFLINE(4005, "对方不在线，请稍后再试"),
    CALL_NOT_EXIST(4006, "通话不存在");

    private final int code;
    private final String message;

    ErrorCode(int code, String message) {
        this.code = code;
        this.message = message;
    }
}
