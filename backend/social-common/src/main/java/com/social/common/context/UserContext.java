package com.social.common.context;

/**
 * 当前登录用户上下文。
 *
 * <p>由 {@code AuthInterceptor} 在请求进入时写入、请求结束时清理。
 * 业务代码通过 {@link #getUserId()} 获取当前用户，避免在参数里层层传递。
 */
public final class UserContext {

    private static final ThreadLocal<Long> USER_ID = new ThreadLocal<>();

    private UserContext() {
    }

    public static void setUserId(Long userId) {
        USER_ID.set(userId);
    }

    public static Long getUserId() {
        return USER_ID.get();
    }

    public static void clear() {
        USER_ID.remove();
    }
}
