package com.social.service.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.social.common.constant.RedisKeys;
import com.social.common.enums.ErrorCode;
import com.social.common.exception.BusinessException;
import com.social.common.util.JwtUtil;
import com.social.service.config.AppProperties;
import com.social.service.dto.LoginVO;
import com.social.service.dto.TokenVO;
import com.social.service.dto.UserVO;
import com.social.service.entity.FeedRule;
import com.social.service.entity.User;
import com.social.service.entity.UserProfile;
import com.social.service.mapper.FeedRuleMapper;
import com.social.service.mapper.UserMapper;
import com.social.service.mapper.UserProfileMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * 认证服务：验证码、注册、登录、刷新令牌、登出。
 */
@Service
@RequiredArgsConstructor
public class AuthService {

    private final UserMapper userMapper;
    private final UserProfileMapper userProfileMapper;
    private final FeedRuleMapper feedRuleMapper;
    private final StringRedisTemplate redis;
    private final AppProperties properties;

    private final BCryptPasswordEncoder passwordEncoder = new BCryptPasswordEncoder();

    /** 验证码发送频率限制（秒） */
    private static final long CODE_SEND_INTERVAL_SECONDS = 60;
    private static final String CODE_RATE_LIMIT_KEY = "rl:code:%s:%s";

    // ---------- 验证码 ----------

    /**
     * 发送验证码。mock 模式（开发/联调）下返回固定验证码，便于本地测试；
     * 生产接入阿里云短信后关闭 mock，改为真实发送并返回脱敏提示。
     */
    public String sendVerifyCode(String phone, String scene) {
        validatePhone(phone);

        String rateKey = String.format(CODE_RATE_LIMIT_KEY, phone, scene);
        Boolean rateOk = redis.opsForValue().setIfAbsent(rateKey, "1", Duration.ofSeconds(CODE_SEND_INTERVAL_SECONDS));
        if (Boolean.FALSE.equals(rateOk)) {
            throw new BusinessException(ErrorCode.RATE_LIMITED, "验证码发送过于频繁，请稍后再试");
        }

        AppProperties.VerifyCode vc = properties.getVerifyCode();
        String code = vc.isMockEnabled()
                ? vc.getMockCode()
                : String.valueOf((int) ((Math.random() * 9 + 1) * 100000));

        String key = RedisKeys.verifyCodeKey(phone, scene);
        redis.opsForValue().set(key, code, Duration.ofSeconds(vc.getExpireSeconds()));

        // TODO(阶段六合规): mock 关闭后接入阿里云短信服务，发送验证码；返回脱敏提示
        return vc.isMockEnabled() ? code : "验证码已发送";
    }

    private void verifyCode(String phone, String scene, String code) {
        String key = RedisKeys.verifyCodeKey(phone, scene);
        String cached = redis.opsForValue().get(key);
        if (cached == null || !cached.equals(code)) {
            throw new BusinessException(ErrorCode.VERIFY_CODE_INVALID);
        }
        redis.delete(key);
    }

    // ---------- 注册 / 登录 ----------

    @Transactional
    public LoginVO register(String phone, String password, String code, String nickname) {
        validatePhone(phone);

        Long count = userMapper.selectCount(new LambdaQueryWrapper<User>().eq(User::getPhone, phone));
        if (count != null && count > 0) {
            throw new BusinessException(ErrorCode.PHONE_ALREADY_REGISTERED);
        }

        verifyCode(phone, RedisKeys.SCENE_REGISTER, code);

        User user = new User();
        user.setPhone(phone);
        user.setPassword(passwordEncoder.encode(password));
        user.setStatus(0);
        user.setDeleted(0);
        userMapper.insert(user);

        // 默认昵称：用户 + 手机尾号
        String defaultNickname = (nickname == null || nickname.isBlank())
                ? "用户" + phone.substring(phone.length() - 4)
                : nickname.trim();

        UserProfile profile = new UserProfile();
        profile.setUserId(user.getId());
        profile.setNickname(defaultNickname);
        profile.setGender(0);
        userProfileMapper.insert(profile);

        // 初始化隐私设置（默认：动态公开、不允许陌生人评论、所有人可私聊）
        FeedRule rule = new FeedRule();
        rule.setUserId(user.getId());
        rule.setMomentsVisible(0);
        rule.setAllowStrangerReply(0);
        rule.setChatPolicy(0);
        feedRuleMapper.insert(rule);

        return buildLoginVO(user);
    }

    public LoginVO loginByPassword(String phone, String password) {
        User user = getUserByPhone(phone);
        if (!passwordEncoder.matches(password, user.getPassword())) {
            throw new BusinessException(ErrorCode.PASSWORD_ERROR);
        }
        return buildLoginVO(user);
    }

    public LoginVO loginByCode(String phone, String code) {
        validatePhone(phone);
        verifyCode(phone, RedisKeys.SCENE_LOGIN, code);
        User user = getUserByPhone(phone);
        return buildLoginVO(user);
    }

    // ---------- 令牌 ----------

    /** 用 refresh token 换新令牌（令牌轮换：旧 refresh 立即失效）。 */
    public TokenVO refresh(String refreshToken) {
        AppProperties.Jwt jwt = properties.getJwt();
        io.jsonwebtoken.Claims claims;
        try {
            claims = JwtUtil.parse(refreshToken, jwt.getRefreshSecret());
        } catch (Exception e) {
            throw new BusinessException(ErrorCode.REFRESH_TOKEN_INVALID);
        }

        Long userId = JwtUtil.getUserId(claims);
        String jti = claims.get("jti", String.class);
        String redisValue = redis.opsForValue().get(RedisKeys.refreshTokenKey(jti));
        if (redisValue == null || !String.valueOf(userId).equals(redisValue)) {
            throw new BusinessException(ErrorCode.REFRESH_TOKEN_INVALID);
        }

        User user = userMapper.selectById(userId);
        if (user == null || user.getStatus() == 1) {
            throw new BusinessException(ErrorCode.USER_NOT_EXIST);
        }

        // 轮换：作废旧 refresh，签发新令牌
        redis.delete(RedisKeys.refreshTokenKey(jti));
        return issueTokens(user);
    }

    public void logout(String refreshToken) {
        try {
            io.jsonwebtoken.Claims claims = JwtUtil.parse(refreshToken, properties.getJwt().getRefreshSecret());
            String jti = claims.get("jti", String.class);
            redis.delete(RedisKeys.refreshTokenKey(jti));
        } catch (Exception ignored) {
            // 令牌非法时登出视为成功，幂等
        }
    }

    // ---------- 内部方法 ----------

    private LoginVO buildLoginVO(User user) {
        TokenVO token = issueTokens(user);
        UserProfile profile = userProfileMapper.selectOne(
                new LambdaQueryWrapper<UserProfile>().eq(UserProfile::getUserId, user.getId()));
        UserVO userVO = toUserVO(user, profile);
        return new LoginVO(token, userVO);
    }

    private TokenVO issueTokens(User user) {
        AppProperties.Jwt jwt = properties.getJwt();
        long accessExpireMs = jwt.getAccessExpireSeconds() * 1000;
        long refreshExpireMs = jwt.getRefreshExpireSeconds() * 1000;

        Map<String, Object> accessClaims = new HashMap<>();
        accessClaims.put("phone", user.getPhone());

        String jti = UUID.randomUUID().toString().replace("-", "");
        Map<String, Object> refreshClaims = new HashMap<>();
        refreshClaims.put("jti", jti);

        String accessToken = JwtUtil.createAccessToken(user.getId(), jwt.getAccessSecret(), accessExpireMs, accessClaims);
        String refreshToken = JwtUtil.createRefreshToken(user.getId(), jwt.getRefreshSecret(), refreshExpireMs, refreshClaims);

        // refresh token 白名单：用于登出与校验
        redis.opsForValue().set(RedisKeys.refreshTokenKey(jti), String.valueOf(user.getId()),
                Duration.ofSeconds(jwt.getRefreshExpireSeconds()));

        return new TokenVO(accessToken, refreshToken, jwt.getAccessExpireSeconds());
    }

    private User getUserByPhone(String phone) {
        User user = userMapper.selectOne(new LambdaQueryWrapper<User>().eq(User::getPhone, phone));
        if (user == null) {
            throw new BusinessException(ErrorCode.PASSWORD_ERROR, "手机号或密码错误");
        }
        if (user.getStatus() == 1) {
            throw new BusinessException(ErrorCode.USER_STATUS_FORBIDDEN);
        }
        return user;
    }

    private void validatePhone(String phone) {
        if (phone == null || !phone.matches("^1[3-9]\\d{9}$")) {
            throw new BusinessException(ErrorCode.PARAM_ERROR, "手机号格式不正确");
        }
    }

    private UserVO toUserVO(User user, UserProfile profile) {
        UserVO vo = new UserVO();
        vo.setId(user.getId());
        vo.setPhone(maskPhone(user.getPhone()));
        vo.setNickname(profile == null ? null : profile.getNickname());
        vo.setAvatar(profile == null ? null : profile.getAvatar());
        vo.setSignature(profile == null ? null : profile.getSignature());
        vo.setGender(profile == null ? null : profile.getGender());
        vo.setRegion(profile == null ? null : profile.getRegion());
        return vo;
    }

    private String maskPhone(String phone) {
        if (phone == null || phone.length() < 7) {
            return phone;
        }
        return phone.substring(0, 3) + "****" + phone.substring(7);
    }
}
