package com.social.common.constant;

/**
 * Redis 键模板。统一集中管理，避免散落各处导致命名冲突。
 */
public final class RedisKeys {

    private RedisKeys() {
    }

    /** 登录验证码：code:{phone}:{scene}，TTL 5min */
    public static final String VERIFY_CODE = "code:%s:%s";

    /** refresh token 白名单：refresh:jti，value 为 userId，TTL 7d */
    public static final String REFRESH_TOKEN = "refresh:%s";

    /** 用户在线状态：online:{userId}，value 为最近心跳时间 */
    public static final String USER_ONLINE = "online:%d";

    /** 用户信息缓存：user:{userId} */
    public static final String USER = "user:%d";

    /** 用户资料缓存：userProfile:{userId} */
    public static final String USER_PROFILE = "userProfile:%d";

    /** 用户封禁标记：ban:user:{userId}，封禁时写入使在线会话立即失效，解封清除 */
    public static final String USER_BAN = "ban:user:%d";

    public static String userBanKey(Long userId) {
        return String.format(USER_BAN, userId);
    }

    /** 验证码场景 */
    public static final String SCENE_REGISTER = "register";
    public static final String SCENE_LOGIN = "login";
    public static final String SCENE_RESET_PASSWORD = "resetPassword";

    public static String verifyCodeKey(String phone, String scene) {
        return String.format(VERIFY_CODE, phone, scene);
    }

    public static String refreshTokenKey(String jti) {
        return String.format(REFRESH_TOKEN, jti);
    }
}
