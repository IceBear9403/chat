package com.social.common.context;

/**
 * 当前登录用户上下文。
 *
 * <p>userId 由 {@code AuthInterceptor} 在请求进入时写入、请求结束时清理；
 * IP / UA 由 {@code RequestIpFilter} 对全量请求（含免认证接口）写入，供日志服务使用。
 * 业务代码通过 {@link #getUserId()} 获取当前用户，避免在参数里层层传递。
 */
public final class UserContext {

    private static final ThreadLocal<Long> USER_ID = new ThreadLocal<>();
    private static final ThreadLocal<String> CLIENT_IP = new ThreadLocal<>();
    private static final ThreadLocal<String> USER_AGENT = new ThreadLocal<>();

    private UserContext() {
    }

    public static void setUserId(Long userId) {
        USER_ID.set(userId);
    }

    public static Long getUserId() {
        return USER_ID.get();
    }

    /** 请求来源 IP（X-Forwarded-For 取首个；无则 remoteAddr），由 RequestIpFilter 写入。 */
    public static void setClientIp(String ip) {
        CLIENT_IP.set(ip);
    }

    public static String getClientIp() {
        return CLIENT_IP.get();
    }

    public static void setUserAgent(String userAgent) {
        USER_AGENT.set(userAgent);
    }

    public static String getUserAgent() {
        return USER_AGENT.get();
    }

    public static void clear() {
        USER_ID.remove();
        CLIENT_IP.remove();
        USER_AGENT.remove();
    }
}
