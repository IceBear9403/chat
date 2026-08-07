package com.social.api.controller;

import com.social.common.model.Result;
import com.social.service.config.AppProperties;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 通话接口：客户端初始化 RTCPeerConnection 前拉取 ICE 服务器配置。
 */
@Tag(name = "通话", description = "WebRTC ICE 配置")
@RestController
@RequestMapping("/api/call")
@RequiredArgsConstructor
public class CallController {

    private final AppProperties appProperties;

    @Operation(summary = "ICE 服务器配置")
    @GetMapping("/config")
    public Result<AppProperties.Webrtc> config() {
        return Result.ok(appProperties.getWebrtc());
    }
}
