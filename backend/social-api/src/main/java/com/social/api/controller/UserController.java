package com.social.api.controller;

import com.social.api.dto.request.DeleteAccountRequest;
import com.social.api.dto.request.RealNameRequest;
import com.social.api.dto.request.UpdatePrivacyRequest;
import com.social.api.dto.request.UpdateProfileRequest;
import com.social.common.context.UserContext;
import com.social.common.model.Result;
import com.social.service.dto.UserVO;
import com.social.service.entity.FeedRule;
import com.social.service.service.UserService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 用户接口：资料、隐私设置。需要登录（JWT）。
 */
@Tag(name = "用户", description = "个人资料 / 隐私设置")
@RestController
@RequestMapping("/api/user")
@RequiredArgsConstructor
public class UserController {

    private final UserService userService;

    @Operation(summary = "获取当前登录用户信息")
    @GetMapping("/me")
    public Result<UserVO> me() {
        return Result.ok(userService.getUserVO(UserContext.getUserId()));
    }

    @Operation(summary = "搜索用户（昵称/手机号，供发起会话）")
    @GetMapping("/search")
    public Result<List<UserVO>> search(@RequestParam String keyword) {
        return Result.ok(userService.searchUsers(UserContext.getUserId(), keyword));
    }

    @Operation(summary = "更新个人资料")
    @PutMapping("/profile")
    public Result<UserVO> updateProfile(@Valid @RequestBody UpdateProfileRequest request) {
        return Result.ok(userService.updateProfile(
                UserContext.getUserId(),
                request.getNickname(),
                request.getAvatar(),
                request.getSignature(),
                request.getGender(),
                request.getRegion()));
    }

    @Operation(summary = "查询隐私设置")
    @GetMapping("/privacy")
    public Result<FeedRule> getPrivacy() {
        return Result.ok(userService.getPrivacy(UserContext.getUserId()));
    }

    @Operation(summary = "更新隐私设置")
    @PutMapping("/privacy")
    public Result<FeedRule> updatePrivacy(@Valid @RequestBody UpdatePrivacyRequest request) {
        return Result.ok(userService.updatePrivacy(
                UserContext.getUserId(),
                request.getMomentsVisible(),
                request.getAllowStrangerReply(),
                request.getChatPolicy()));
    }

    @Operation(summary = "实名认证（阶段七合规）")
    @PostMapping("/realname")
    public Result<UserVO> bindRealName(@Valid @RequestBody RealNameRequest request) {
        return Result.ok(userService.bindRealName(
                UserContext.getUserId(),
                request.getRealName(),
                request.getIdCard()));
    }

    @Operation(summary = "注销账号（需密码确认）")
    @PostMapping("/delete-account")
    public Result<Void> deleteAccount(@Valid @RequestBody DeleteAccountRequest request) {
        userService.deleteAccount(UserContext.getUserId(), request.getPassword());
        return Result.ok();
    }
}
