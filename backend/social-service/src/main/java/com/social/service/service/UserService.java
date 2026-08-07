package com.social.service.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.social.common.enums.ErrorCode;
import com.social.common.exception.BusinessException;
import com.social.service.dto.UserVO;
import com.social.service.entity.FeedRule;
import com.social.service.entity.User;
import com.social.service.entity.UserProfile;
import com.social.service.mapper.FeedRuleMapper;
import com.social.service.mapper.UserMapper;
import com.social.service.mapper.UserProfileMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

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
        for (User u : userMapper.selectList(new LambdaQueryWrapper<User>()
                .like(User::getPhone, like)
                .last("limit 20"))) {
            ids.add(u.getId());
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

    /** 查询用户资料（对外）。若不存在抛业务异常。 */
    public UserVO getUserVO(Long userId) {
        User user = userMapper.selectById(userId);
        if (user == null || user.getStatus() == 1) {
            throw new BusinessException(ErrorCode.USER_NOT_EXIST);
        }
        UserProfile profile = getProfile(userId);
        return toVO(user, profile);
    }

    @Transactional
    public UserVO updateProfile(Long userId, String nickname, String avatar, String signature, Integer gender, String region) {
        UserProfile profile = getProfile(userId);
        if (nickname != null && !nickname.isBlank()) {
            if (nickname.length() > 20) {
                throw new BusinessException(ErrorCode.PARAM_ERROR, "昵称最长 20 个字符");
            }
            profile.setNickname(nickname.trim());
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

    private UserVO toVO(User user, UserProfile profile) {
        UserVO vo = new UserVO();
        vo.setId(user.getId());
        vo.setPhone(maskPhone(user.getPhone()));
        vo.setNickname(profile.getNickname());
        vo.setAvatar(profile.getAvatar());
        vo.setSignature(profile.getSignature());
        vo.setGender(profile.getGender());
        vo.setRegion(profile.getRegion());
        return vo;
    }

    private String maskPhone(String phone) {
        if (phone == null || phone.length() < 7) {
            return phone;
        }
        return phone.substring(0, 3) + "****" + phone.substring(7);
    }
}
