package com.social.service.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.social.common.constant.RedisKeys;
import com.social.common.enums.ErrorCode;
import com.social.common.exception.BusinessException;
import com.social.common.util.DigestUtil;
import com.social.common.util.PiiCrypto;
import com.social.service.config.AppProperties;
import com.social.service.config.SensitiveWordFilter;
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

import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * 用户服务：资料查询与更新。
 */
@Service
@RequiredArgsConstructor
public class UserService {

    private final UserMapper userMapper;
    private final UserProfileMapper userProfileMapper;
    private final FeedRuleMapper feedRuleMapper;
    private final StringRedisTemplate redis;
    private final AppProperties properties;
    private final UserLogService userLogService;
    private final SensitiveWordFilter sensitiveWordFilter;

    private final BCryptPasswordEncoder passwordEncoder = new BCryptPasswordEncoder();

    /** 按昵称/手机号搜索用户（供发起会话）。排除自己、封禁用户，最多 20 条。 */
    public List<UserVO> searchUsers(Long selfId, String keyword) {
        if (keyword == null || keyword.isBlank()) {
            return List.of();
        }
        String like = keyword.trim();
        Set<Long> ids = new LinkedHashSet<>();
        for (UserProfile p : userProfileMapper.selectList(new LambdaQueryWrapper<UserProfile>()
                .like(UserProfile::getNickname, like)
                .last("limit 20"))) {
            ids.add(p.getUserId());
        }
        // 手机号/邮箱已加密存储，无法 LIKE；完整手机号按 phone_hash、邮箱按 email_hash 精确匹配
        if (like.matches("^1[3-9]\\d{9}$")) {
            User u = userMapper.selectOne(new LambdaQueryWrapper<User>()
                    .eq(User::getPhoneHash, DigestUtil.sha256Hex(like)));
            if (u != null) {
                ids.add(u.getId());
            }
        }
        if (like.contains("@")) {
            User u = userMapper.selectOne(new LambdaQueryWrapper<User>()
                    .eq(User::getEmailHash, DigestUtil.sha256Hex(like.toLowerCase())));
            if (u != null) {
                ids.add(u.getId());
            }
        }
        List<UserVO> result = new ArrayList<>();
        for (Long id : ids) {
            if (id.equals(selfId)) {
                continue;
            }
            try {
                result.add(getUserVO(id));
            } catch (BusinessException ignored) {
                // 用户已注销/封禁，跳过
            }
        }
        return result;
    }

    /** 查询用户资料（对外）。若不存在/封禁/注销抛业务异常。 */
    public UserVO getUserVO(Long userId) {
        User user = userMapper.selectById(userId);
        if (user == null || user.getStatus() != 0) {
            throw new BusinessException(ErrorCode.USER_NOT_EXIST);
        }
        UserProfile profile = getProfile(userId);
        return toVO(user, profile);
    }

    /**
     * 实名认证（阶段七合规）。
     * mock 模式下直接通过（记录姓名+身份证，加密存储）；接入阿里云实人认证后置 mock=false。
     */
    @Transactional
    public UserVO bindRealName(Long userId, String realName, String idCard) {
        if (realName == null || realName.trim().isEmpty() || realName.trim().length() > 20) {
            throw new BusinessException(ErrorCode.PARAM_ERROR, "请输入真实姓名（2-20 字）");
        }
        if (idCard == null || !idCard.trim().matches("^\\d{17}[0-9Xx]$")) {
            throw new BusinessException(ErrorCode.PARAM_ERROR, "请输入 18 位身份证号");
        }
        if (!properties.getRealname().isMockEnabled()) {
            // 接入阿里云实人认证 / 手机号三要素后在此核验（当前未接入，直接拒绝避免虚假实名）
            throw new BusinessException(ErrorCode.PARAM_ERROR, "实名认证服务尚未开通，请稍后再试");
        }
        UserProfile profile = getProfile(userId);
        profile.setRealName(PiiCrypto.encrypt(realName.trim(), piiKey()));
        profile.setIdCard(PiiCrypto.encrypt(idCard.trim().toUpperCase(), piiKey()));
        profile.setRealnameStatus(1);
        userProfileMapper.updateById(profile);

        userLogService.write(userId, "realname", "实名认证通过");
        User user = userMapper.selectById(userId);
        return toVO(user, profile);
    }

    /**
     * 注销账号（阶段七数据合规）。需校验当前登录密码。
     * 置为已注销，抹除姓名/身份证等敏感信息，并写入封禁标记使在线会话立即失效。
     */
    @Transactional
    public void deleteAccount(Long userId, String password) {
        User user = userMapper.selectById(userId);
        if (user == null || user.getStatus() != 0) {
            throw new BusinessException(ErrorCode.USER_NOT_EXIST);
        }
        if (password == null || !passwordEncoder.matches(password, user.getPassword())) {
            throw new BusinessException(ErrorCode.PASSWORD_ERROR, "密码不正确");
        }

        user.setStatus(2);
        userMapper.updateById(user);

        // 抹除实名等敏感信息（手机号保留用于查重，但账号不可再登录）
        UserProfile profile = getProfile(userId);
        profile.setRealName(null);
        profile.setIdCard(null);
        profile.setRealnameStatus(0);
        userProfileMapper.updateById(profile);

        // 使在线会话立即失效（访问令牌被拦截器按封禁标记拦截）
        redis.opsForValue().set(RedisKeys.userBanKey(userId), "1");

        userLogService.write(userId, "deleteAccount", "注销账号");
    }

    @Transactional
    public UserVO updateProfile(Long userId, String nickname, String avatar, String signature, Integer gender, String region) {
        UserProfile profile = getProfile(userId);
        if (nickname != null && !nickname.isBlank()) {
            String trimmed = nickname.trim();
            if (trimmed.length() > 20) {
                throw new BusinessException(ErrorCode.PARAM_ERROR, "昵称最长 20 个字符");
            }
            // 昵称未变化则跳过校验（不触发敏感词/修改间隔限制）
            if (!trimmed.equals(profile.getNickname())) {
                checkNicknameChange(profile, trimmed);
                profile.setNickname(trimmed);
                profile.setNicknameUpdatedAt(LocalDateTime.now());
            }
        }
        if (avatar != null) {
            profile.setAvatar(avatar);
        }
        if (signature != null) {
            if (signature.length() > 100) {
                throw new BusinessException(ErrorCode.PARAM_ERROR, "个性签名最长 100 个字符");
            }
            profile.setSignature(signature);
        }
        if (gender != null) {
            if (gender < 0 || gender > 2) {
                throw new BusinessException(ErrorCode.PARAM_ERROR, "性别参数不正确");
            }
            profile.setGender(gender);
        }
        if (region != null) {
            profile.setRegion(region);
        }
        userProfileMapper.updateById(profile);

        User user = userMapper.selectById(userId);
        return toVO(user, profile);
    }

    /** 查询隐私设置。 */
    public FeedRule getPrivacy(Long userId) {
        FeedRule rule = feedRuleMapper.selectOne(
                new LambdaQueryWrapper<FeedRule>().eq(FeedRule::getUserId, userId));
        if (rule == null) {
            // 容错：早期注册缺失时补建默认行
            rule = new FeedRule();
            rule.setUserId(userId);
            rule.setMomentsVisible(0);
            rule.setAllowStrangerReply(0);
            rule.setChatPolicy(0);
            feedRuleMapper.insert(rule);
        }
        return rule;
    }

    @Transactional
    public FeedRule updatePrivacy(Long userId, Integer momentsVisible, Integer allowStrangerReply, Integer chatPolicy) {
        FeedRule rule = getPrivacy(userId);
        if (momentsVisible != null && (momentsVisible < 0 || momentsVisible > 2)) {
            throw new BusinessException(ErrorCode.PARAM_ERROR, "可见范围参数不正确");
        }
        if (allowStrangerReply != null && allowStrangerReply < 0) {
            throw new BusinessException(ErrorCode.PARAM_ERROR, "参数不正确");
        }
        if (chatPolicy != null && (chatPolicy < 0 || chatPolicy > 2)) {
            throw new BusinessException(ErrorCode.PARAM_ERROR, "私聊策略参数不正确");
        }
        if (momentsVisible != null) {
            rule.setMomentsVisible(momentsVisible);
        }
        if (allowStrangerReply != null) {
            rule.setAllowStrangerReply(allowStrangerReply);
        }
        if (chatPolicy != null) {
            rule.setChatPolicy(chatPolicy);
        }
        feedRuleMapper.updateById(rule);
        return rule;
    }

    private UserProfile getProfile(Long userId) {
        UserProfile profile = userProfileMapper.selectOne(
                new LambdaQueryWrapper<UserProfile>().eq(UserProfile::getUserId, userId));
        if (profile == null) {
            throw new BusinessException(ErrorCode.USER_NOT_EXIST);
        }
        return profile;
    }

    /** 昵称修改前置校验：敏感词拦截 + 修改间隔限制（间隔由 app.nickname.change-interval-days 配置，管理员不受限）。 */
    private void checkNicknameChange(UserProfile profile, String nickname) {
        if (sensitiveWordFilter.contains(nickname)) {
            throw new BusinessException(ErrorCode.NICKNAME_ILLEGAL, "昵称包含敏感词，请更换后重试");
        }
        long intervalDays = properties.getNickname().getChangeIntervalDays();
        if (intervalDays > 0 && profile.getNicknameUpdatedAt() != null) {
            LocalDateTime now = LocalDateTime.now();
            long elapsedDays = ChronoUnit.DAYS.between(profile.getNicknameUpdatedAt(), now);
            if (elapsedDays < intervalDays) {
                long remain = intervalDays - Math.max(elapsedDays, 0);
                throw new BusinessException(ErrorCode.NICKNAME_CHANGE_TOO_FREQUENT,
                        "昵称修改过于频繁，请 " + remain + " 天后重试");
            }
        }
    }

    private UserVO toVO(User user, UserProfile profile) {
        UserVO vo = new UserVO();
        vo.setId(user.getId());
        vo.setPhone(maskPhone(PiiCrypto.decrypt(user.getPhone(), piiKey())));
        vo.setEmail(maskEmail(PiiCrypto.decrypt(user.getEmail(), piiKey())));
        vo.setNickname(profile.getNickname());
        vo.setAvatar(profile.getAvatar());
        vo.setSignature(profile.getSignature());
        vo.setGender(profile.getGender());
        vo.setRegion(profile.getRegion());
        vo.setIsAdmin(Integer.valueOf(1).equals(user.getIsAdmin()));
        vo.setRealnameStatus(profile.getRealnameStatus());
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
