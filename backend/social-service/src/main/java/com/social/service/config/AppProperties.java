package com.social.service.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * 应用自定义配置（application.yml 中 app.* 前缀）。
 */
@Data
@Component
@ConfigurationProperties(prefix = "app")
public class AppProperties {

    /** JWT 配置 */
    private final Jwt jwt = new Jwt();

    /** 验证码配置（生产接入短信服务后，关闭 mock 模式） */
    private final VerifyCode verifyCode = new VerifyCode();

    /** 文件存储配置（本地实现；生产切换 OSS 后改用对象存储，见 StorageService 抽象） */
    private final Storage storage = new Storage();

    /** WebRTC 配置（音视频通话 ICE 服务器） */
    private final Webrtc webrtc = new Webrtc();

    @Data
    public static class Jwt {
        /** access token 密钥（>= 32 字符） */
        private String accessSecret;
        /** access token 有效期（秒），默认 2h */
        private long accessExpireSeconds = 7200;
        /** refresh token 密钥（>= 32 字符，与 access 不同） */
        private String refreshSecret;
        /** refresh token 有效期（秒），默认 7d */
        private long refreshExpireSeconds = 604800;
    }

    @Data
    public static class Storage {
        /** 本地文件存储根目录（相对启动目录）。生产切 OSS 后可忽略 */
        private String localPath = "./uploads";
        /** 单个文件大小上限（MB） */
        private long maxFileSizeMb = 20;
    }

    /** WebRTC ICE 服务器列表。生产接入自建 coturn（阶段六）后替换 URL 与凭据。 */
    @Data
    public static class Webrtc {
        /** ICE 服务器，格式 [{urls, username, credential}] */
        private List<IceServer> iceServers = new ArrayList<>();
    }

    @Data
    public static class IceServer {
        /** 单个或多个 STUN/TURN 地址，如 "stun:stun.l.google.com:19302" */
        private String urls;
        /** TURN 凭据（STUN 可留空） */
        private String username;
        private String credential;
    }

    @Data
    public static class VerifyCode {
        /** 开发阶段 mock 开关：开启后使用固定验证码，不真正发短信 */
        private boolean mockEnabled = true;
        /** mock 验证码 */
        private String mockCode = "123456";
        /** 验证码有效期（秒），默认 5min */
        private long expireSeconds = 300;
        /** 短信模板签名（接入阿里云短信后使用） */
        private String smsSignName = "";
        private String smsTemplateCode = "";
    }
}
