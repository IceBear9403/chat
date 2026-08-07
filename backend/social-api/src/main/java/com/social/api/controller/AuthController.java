package com.social.api.controller;

import com.social.api.dto.request.CodeLoginRequest;
import com.social.api.dto.request.LoginRequest;
import com.social.api.dto.request.RefreshRequest;
import com.social.api.dto.request.RegisterRequest;
import com.social.api.dto.request.SendCodeRequest;
import com.social.common.model.Result;
import com.social.service.dto.LoginVO;
import com.social.service.dto.TokenVO;
import com.social.service.service.AuthService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 认证接口：验证码、注册、登录、刷新令牌、登出。
 */
@Tag(name = "认证", description = "注册 / 登录 / 令牌")
@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService;

    @Operation(summary = "发送验证码", description = "mock 模式下返回固定验证码（app.verify-code.mock-code，默认 123456）")
    @PostMapping("/send-code")
    public Result<String> sendCode(@Valid @RequestBody SendCodeRequest request) {
        return Result.ok(authService.sendVerifyCode(request.getPhone(), request.getScene()));
    }

    @Operation(summary = "注册")
    @PostMapping("/register")
    public Result<LoginVO> register(@Valid @RequestBody RegisterRequest request) {
        return Result.ok(authService.register(
                request.getPhone(), request.getPassword(), request.getCode(), request.getNickname()));
    }

    @Operation(summary = "密码登录")
    @PostMapping("/login")
    public Result<LoginVO> login(@Valid @RequestBody LoginRequest request) {
        return Result.ok(authService.loginByPassword(request.getPhone(), request.getPassword()));
    }

    @Operation(summary = "验证码登录")
    @PostMapping("/login/code")
    public Result<LoginVO> loginByCode(@Valid @RequestBody CodeLoginRequest request) {
        return Result.ok(authService.loginByCode(request.getPhone(), request.getCode()));
    }

    @Operation(summary = "刷新令牌")
    @PostMapping("/refresh")
    public Result<TokenVO> refresh(@Valid @RequestBody RefreshRequest request) {
        return Result.ok(authService.refresh(request.getRefreshToken()));
    }

    @Operation(summary = "登出")
    @PostMapping("/logout")
    public Result<Void> logout(@Valid @RequestBody RefreshRequest request) {
        authService.logout(request.getRefreshToken());
        return Result.ok();
    }
}
