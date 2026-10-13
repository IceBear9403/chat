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

    /** 认证模式配置（手机验证 / 邮箱验证，改 mode 即可切换） */
    private final Auth auth = new Auth();

    /** 认证模式：phone（手机验证）/ email（邮箱验证）。手机验证相关代码保留，切换即生效。 */
    @Data
    public static class Auth {
        /** 当前生效的认证模式，默认手机验证 */
        private String mode = "phone";
    }

    /** 验证码配置（生产接入短信服务后，关闭 mock 模式） */
    private final VerifyCode verifyCode = new VerifyCode();

    /** 文件存储配置（本地实现；生产切换 OSS 后改用对象存储，见 StorageService 抽象） */
    private final Storage storage = new Storage();

    /** WebRTC 配置（音视频通话 ICE 服务器） */
    private final Webrtc webrtc = new Webrtc();

    /** PII 加密配置（阶段七数据合规） */
    private final Pii pii = new Pii();

    /** 实名认证配置（阶段七合规；mock 可过，接入阿里云短信后置 false） */
    private final RealName realname = new RealName();

    /** 合规定时任务配置（阶段七；多实例部署关闭避免重复扫描） */
    private final Compliance compliance = new Compliance();

    /** 邮件发送配置（邮箱验证模式真实发信，SMTP 服务器参数见 spring.mail） */
    private final Mail mail = new Mail();

    /** 昵称配置（修改间隔限制、违禁昵称替换） */
    private final Nickname nickname = new Nickname();

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

    /** PII 加密配置（手机号 / 姓名 / 身份证号 AES-GCM 加密，见 PiiCrypto） */
    @Data
    public static class Pii {
        /** 加密密钥（>=16 字符）。生产用环境变量 APP_PII_KEY 注入，不得落明文 */
        private String key = "";
    }

    /** 实名认证配置（先记录姓名+身份证，接入阿里云实人认证后置 false 并扩展核验） */
    @Data
    public static class RealName {
        /** mock 开关：开启时实名直接通过（当前无短信/实人服务），生产接入后置 false */
        private boolean mockEnabled = true;
    }

    /** 合规定时任务配置（日志清理 / 敏感词巡检） */
    @Data
    public static class Compliance {
        /** 任务开关：多实例部署时置 false，避免多个实例重复扫描 */
        private boolean jobEnabled = true;
    }

    /**
     * 邮箱验证码真实发信配置。SMTP 服务器参数（host/port/username/password）在 spring.mail 配置，
     * 这里只放业务相关开关与发件人。
     */
    @Data
    public static class Mail {
        /** 真实发信开关：true=走 SMTP 发送验证码邮件；false=退回 mock 固定码（本地联调） */
        private boolean enabled = true;
        /** 发件人地址（通常与 SMTP 账号一致，QQ 邮箱要求与账号相同） */
        private String from = "";
    }

    /**
     * 昵称配置（阶段九）：
     * 用户自行修改昵称需满足最小间隔；定时任务把含违禁词昵称替换为「违禁昵称+时间戳」。
     */
    @Data
    public static class Nickname {
        /** 用户自行修改昵称的最小间隔（天），0=不限制。管理员修改昵称不受此限制 */
        private long changeIntervalDays = 7;
        /** 违禁昵称替换前缀（昵称巡检定时任务用），最终形如 "违禁昵称<毫秒时间戳>" */
        private String bannedPrefix = "违禁昵称";
    }
}
