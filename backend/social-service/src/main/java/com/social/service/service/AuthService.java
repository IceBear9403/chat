package com.social.service.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.social.common.constant.RedisKeys;
import com.social.common.enums.ErrorCode;
import com.social.common.exception.BusinessException;
import com.social.common.util.DigestUtil;
import com.social.common.util.JwtUtil;
import com.social.common.util.PiiCrypto;
import com.social.service.config.AppProperties;
import com.social.service.dto.AuthConfigVO;
import com.social.service.dto.LoginVO;
import com.social.service.dto.TokenVO;
import com.social.service.dto.UserVO;
import com.social.service.entity.FeedRule;
import com.social.service.entity.User;
import com.social.service.entity.UserProfile;
import com.social.service.mapper.FeedRuleMapper;
import com.social.service.mapper.UserMapper;
import com.social.service.mapper.UserProfileMapper;
import jakarta.mail.MessagingException;
import jakarta.mail.internet.MimeMessage;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.mail.MailException;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
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
@Slf4j
@Service
@RequiredArgsConstructor
public class AuthService {

    private final UserMapper userMapper;
    private final UserProfileMapper userProfileMapper;
    private final FeedRuleMapper feedRuleMapper;
    private final StringRedisTemplate redis;
    private final AppProperties properties;
    private final UserLogService userLogService;
    /** 邮件发送器（spring-boot-starter-mail 自动配置，服务器参数见 spring.mail） */
    private final JavaMailSender mailSender;

    private final BCryptPasswordEncoder passwordEncoder = new BCryptPasswordEncoder();

    /** 验证码发送频率限制（秒） */
    private static final long CODE_SEND_INTERVAL_SECONDS = 60;
    private static final String CODE_RATE_LIMIT_KEY = "rl:code:%s:%s";

    private static final String EMAIL_REGEX = "^[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}$";

    // ---------- 认证配置 ----------

    /** 当前认证模式：phone=手机验证，email=邮箱验证（改 app.auth.mode 切换）。 */
    public AuthConfigVO authConfig() {
        AuthConfigVO vo = new AuthConfigVO();
        vo.setAuthMode(isEmailMode() ? "email" : "phone");
        return vo;
    }

    // ---------- 验证码 ----------

    /**
     * 发送验证码。account 为当前认证模式下的账号（手机号/邮箱）。
     * 邮箱模式且已开启真实发信（app.mail.enabled=true 且发件人已配置）时走 SMTP 发送随机验证码；
     * 否则按 verify-code.mock-enabled：开启返回固定验证码便于本地联调，关闭则返回脱敏提示。
     */
    public String sendVerifyCode(String account, String scene) {
        String target = normalizeAccount(account);

        String rateKey = String.format(CODE_RATE_LIMIT_KEY, target, scene);
        Boolean rateOk = redis.opsForValue().setIfAbsent(rateKey, "1", Duration.ofSeconds(CODE_SEND_INTERVAL_SECONDS));
        if (Boolean.FALSE.equals(rateOk)) {
            throw new BusinessException(ErrorCode.RATE_LIMITED, "验证码发送过于频繁，请稍后再试");
        }

        AppProperties.VerifyCode vc = properties.getVerifyCode();
        // 邮箱模式且已配置真实发信：走 SMTP 发送随机验证码（mock 固定码仅用于未接发信渠道时的联调）
        boolean sendByMail = isEmailMode() && mailSendingEnabled();
        String code = sendByMail
                ? randomCode()
                : (vc.isMockEnabled() ? vc.getMockCode() : randomCode());

        String key = RedisKeys.verifyCodeKey(target, scene);
        redis.opsForValue().set(key, code, Duration.ofSeconds(vc.getExpireSeconds()));

        if (sendByMail) {
            sendCodeMail(target, code);
            return "验证码已发送";
        }
        // TODO(阶段八): mock 关闭后，手机验证接入阿里云短信；返回脱敏提示
        return vc.isMockEnabled() ? code : "验证码已发送";
    }

    /** 邮箱模式真实发信是否可用：开关开启且发件人已配置（未配置则退回 mock，避免误配即报错）。 */
    private boolean mailSendingEnabled() {
        AppProperties.Mail mail = properties.getMail();
        return mail.isEnabled() && mail.getFrom() != null && !mail.getFrom().isBlank();
    }

    /** 生成 6 位随机数字验证码。 */
    private String randomCode() {
        return String.valueOf((int) ((Math.random() * 9 + 1) * 100000));
    }

    /** SMTP 发送验证码邮件。发送失败抛业务异常（验证码未生效，前端提示重试）。 */
    private void sendCodeMail(String to, String code) {
        long minutes = properties.getVerifyCode().getExpireSeconds() / 60;
        try {
            MimeMessage message = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(message, true, "UTF-8");
            helper.setFrom(properties.getMail().getFrom());
            helper.setTo(to);
            helper.setSubject("【社交圈】验证码");
            // 同时提供纯文本与 HTML 两个版本（收件端不支持 HTML 时自动回退纯文本）
            helper.setText(buildCodeMailText(code, minutes), buildCodeMailHtml(code, minutes));
            mailSender.send(message);
            log.info("邮箱验证码已发送 to={}", to);
        } catch (MessagingException | MailException e) {
            log.error("邮箱验证码发送失败 to={}", to, e);
            throw new BusinessException(ErrorCode.MAIL_SEND_FAILED);
        }
    }

    /** 纯文本版（收件端不支持 HTML 时兜底）。 */
    private String buildCodeMailText(String code, long minutes) {
        return "您正在操作社交圈账号，本次验证码为：" + code + "\n"
                + "验证码 " + minutes + " 分钟内有效，请勿泄露给他人。";
    }

    /** HTML 版：品牌色头部 + 卡片布局 + 大号等距验证码 + 安全提示。样式全部内联，兼容主流邮箱客户端。 */
    private String buildCodeMailHtml(String code, long minutes) {
        return "<div style=\"background-color:#f2f4f7;padding:28px 12px;\">"
                + "<table role=\"presentation\" width=\"100%\" cellpadding=\"0\" cellspacing=\"0\" border=\"0\"><tr><td align=\"center\">"
                + "<table role=\"presentation\" width=\"100%\" style=\"max-width:520px;background:#ffffff;border-radius:12px;overflow:hidden;box-shadow:0 2px 12px rgba(0,0,0,0.08);\">"
                + "  <tr><td style=\"background:#1e90ff;padding:18px 28px;\">"
                + "    <span style=\"color:#ffffff;font-size:18px;font-weight:600;\">社交圈</span>"
                + "  </td></tr>"
                + "  <tr><td style=\"padding:28px 28px 8px;\">"
                + "    <div style=\"font-size:15px;color:#333333;line-height:1.8;\">你好，</div>"
                + "    <div style=\"font-size:15px;color:#333333;line-height:1.8;margin-top:6px;\">你正在使用社交圈进行<b>账号操作</b>，以下是本次验证码：</div>"
                + "  </td></tr>"
                + "  <tr><td style=\"padding:20px 28px;text-align:center;\">"
                + "    <div style=\"display:inline-block;background:#f0f7ff;border:1px dashed #1e90ff;border-radius:10px;padding:14px 30px;font-size:30px;font-weight:700;color:#1e90ff;letter-spacing:6px;\">"
                + code
                + "    </div>"
                + "  </td></tr>"
                + "  <tr><td style=\"padding:8px 28px 28px;\">"
                + "    <div style=\"font-size:14px;color:#666666;line-height:1.8;\">"
                + "      验证码 <b>" + minutes + " 分钟内有效</b>，请勿向任何人泄露。"
                + "      <br>如果这不是你的操作，请忽略此邮件，并留意账号安全。"
                + "    </div>"
                + "  </td></tr>"
                + "  <tr><td style=\"padding:16px 28px;background:#fafbfc;border-top:1px solid #eef0f2;\">"
                + "    <div style=\"font-size:12px;color:#999999;line-height:1.7;text-align:center;\">"
                + "      此邮件由系统自动发送，请勿直接回复。"
                + "      <br>© 社交圈"
                + "    </div>"
                + "  </td></tr>"
                + "</table>"
                + "</td></tr></table>"
                + "</div>";
    }

    private void verifyCode(String target, String scene, String code) {
        String key = RedisKeys.verifyCodeKey(target, scene);
        String cached = redis.opsForValue().get(key);
        if (cached == null || !cached.equals(code)) {
            throw new BusinessException(ErrorCode.VERIFY_CODE_INVALID);
        }
        redis.delete(key);
    }

    // ---------- 注册 / 登录 ----------

    @Transactional
    public LoginVO register(String account, String password, String code, String nickname) {
        String target = normalizeAccount(account);

        User user = new User();
        if (isEmailMode()) {
            // 邮箱验证：邮箱加密存储 + email_hash 查重/登录
            String emailHash = emailHashOf(target);
            Long count = userMapper.selectCount(new LambdaQueryWrapper<User>()
                    .eq(User::getEmailHash, emailHash));
            if (count != null && count > 0) {
                throw new BusinessException(ErrorCode.EMAIL_ALREADY_REGISTERED);
            }
            verifyCode(target, RedisKeys.SCENE_REGISTER, code);
            user.setEmail(PiiCrypto.encrypt(target, piiKey()));
            user.setEmailHash(emailHash);
        } else {
            // 手机验证：手机号加密存储 + phone_hash 查重/登录（原逻辑保留）
            validatePhone(target);
            String phoneHash = phoneHashOf(target);
            Long count = userMapper.selectCount(new LambdaQueryWrapper<User>()
                    .eq(User::getPhoneHash, phoneHash));
            if (count != null && count > 0) {
                throw new BusinessException(ErrorCode.PHONE_ALREADY_REGISTERED);
            }
            verifyCode(target, RedisKeys.SCENE_REGISTER, code);
            user.setPhone(PiiCrypto.encrypt(target, piiKey()));
            user.setPhoneHash(phoneHash);
        }

        user.setPassword(passwordEncoder.encode(password));
        user.setStatus(0);
        user.setDeleted(0);
        user.setIsAdmin(0);
        userMapper.insert(user);

        // 默认昵称：手机模式=用户+手机尾号；邮箱模式=用户+邮箱前缀
        String defaultNickname = (nickname == null || nickname.isBlank())
                ? defaultNicknameFor(target)
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

        LoginVO vo = buildLoginVO(user);
        userLogService.write(user.getId(), "register", "注册账号");
        return vo;
    }

    public LoginVO loginByPassword(String account, String password) {
        String target = normalizeAccount(account);
        User user = getUserByAccount(target);
        if (!passwordEncoder.matches(password, user.getPassword())) {
            userLogService.write(user.getId(), "login", "密码登录失败");
            throw new BusinessException(ErrorCode.PASSWORD_ERROR);
        }
        userLogService.write(user.getId(), "login", "密码登录成功");
        return buildLoginVO(user);
    }

    public LoginVO loginByCode(String account, String code) {
        String target = normalizeAccount(account);
        verifyCode(target, RedisKeys.SCENE_LOGIN, code);
        User user = getUserByAccount(target);
        userLogService.write(user.getId(), "loginCode", "验证码登录成功");
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
        if (user == null || user.getStatus() != 0) {
            // 封禁/注销账号不允许刷新令牌
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
            userLogService.write(JwtUtil.getUserId(claims), "logout", "退出登录");
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
        // 邮箱模式用户无手机号，claims 仅放非空字段（null 值不允许写进 JWT）
        String phone = user.getPhone() == null ? null : PiiCrypto.decrypt(user.getPhone(), piiKey());
        String email = user.getEmail() == null ? null : PiiCrypto.decrypt(user.getEmail(), piiKey());
        if (phone != null) {
            accessClaims.put("phone", phone);
        }
        if (email != null) {
            accessClaims.put("email", email);
        }

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

    private User getUserByAccount(String target) {
        // 账号加密存储，检索走 hash（SHA-256）：手机模式=phone_hash，邮箱模式=email_hash
        LambdaQueryWrapper<User> wrapper = new LambdaQueryWrapper<>();
        if (isEmailMode()) {
            wrapper.eq(User::getEmailHash, emailHashOf(target));
        } else {
            wrapper.eq(User::getPhoneHash, phoneHashOf(target));
        }
        User user = userMapper.selectOne(wrapper);
        if (user == null) {
            throw new BusinessException(ErrorCode.PASSWORD_ERROR, isEmailMode() ? "邮箱或密码错误" : "手机号或密码错误");
        }
        if (user.getStatus() == 1) {
            throw new BusinessException(ErrorCode.USER_STATUS_FORBIDDEN);
        }
        if (user.getStatus() == 2) {
            throw new BusinessException(ErrorCode.PASSWORD_ERROR, "账号已注销");
        }
        return user;
    }

    private void validatePhone(String phone) {
        if (phone == null || !phone.matches("^1[3-9]\\d{9}$")) {
            throw new BusinessException(ErrorCode.PARAM_ERROR, "手机号格式不正确");
        }
    }

    /** 按当前认证模式校验并规整账号：手机模式=校验手机号；邮箱模式=小写化邮箱。 */
    private String normalizeAccount(String account) {
        if (isEmailMode()) {
            return normalizeEmail(account);
        }
        validatePhone(account);
        return account;
    }

    private String normalizeEmail(String email) {
        if (email == null || !email.matches(EMAIL_REGEX)) {
            throw new BusinessException(ErrorCode.PARAM_ERROR, "邮箱格式不正确");
        }
        return email.trim().toLowerCase();
    }

    private boolean isEmailMode() {
        return "email".equalsIgnoreCase(properties.getAuth().getMode());
    }

    /** 手机号 SHA-256 哈希（检索键，不反向还原）。 */
    private String phoneHashOf(String phone) {
        return DigestUtil.sha256Hex(phone);
    }

    /** 邮箱 SHA-256 哈希（检索键，不反向还原；邮箱统一小写）。 */
    private String emailHashOf(String email) {
        return DigestUtil.sha256Hex(email.toLowerCase());
    }

    /** 默认昵称：手机模式=用户+手机尾号；邮箱模式=用户+邮箱前缀（净化后，最多 6 位）。 */
    private String defaultNicknameFor(String target) {
        if (isEmailMode()) {
            String local = target.contains("@") ? target.substring(0, target.indexOf('@')) : target;
            String safe = local.replaceAll("[^a-zA-Z0-9]", "");
            if (safe.isEmpty()) {
                return "用户";
            }
            return "用户" + (safe.length() > 6 ? safe.substring(0, 6) : safe);
        }
        return "用户" + target.substring(target.length() - 4);
    }

    private UserVO toUserVO(User user, UserProfile profile) {
        UserVO vo = new UserVO();
        vo.setId(user.getId());
        vo.setPhone(maskPhone(PiiCrypto.decrypt(user.getPhone(), piiKey())));
        vo.setEmail(maskEmail(PiiCrypto.decrypt(user.getEmail(), piiKey())));
        vo.setNickname(profile == null ? null : profile.getNickname());
        vo.setAvatar(profile == null ? null : profile.getAvatar());
        vo.setSignature(profile == null ? null : profile.getSignature());
        vo.setGender(profile == null ? null : profile.getGender());
        vo.setRegion(profile == null ? null : profile.getRegion());
        vo.setIsAdmin(Integer.valueOf(1).equals(user.getIsAdmin()));
        vo.setRealnameStatus(profile == null ? 0 : profile.getRealnameStatus());
        return vo;
    }

    private String maskPhone(String phone) {
        if (phone == null || phone.length() < 7) {
            return phone;
        }
        return phone.substring(0, 3) + "****" + phone.substring(7);
    }

    /** 邮箱脱敏：保留前两位 + @域名，如 al***@example.com。 */
    private String maskEmail(String email) {
        if (email == null || !email.contains("@")) {
            return email;
        }
        int at = email.indexOf('@');
        String local = email.substring(0, at);
        if (local.length() <= 2) {
            return local.charAt(0) + "***" + email.substring(at);
        }
        return local.substring(0, 2) + "***" + email.substring(at);
    }

    private String piiKey() {
        return properties.getPii().getKey();
    }
}
