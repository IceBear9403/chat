package com.social.common.util;

import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Base64;

/**
 * 个人信息（PII）加密：AES-256-GCM 认证加密（阶段七数据合规）。
 *
 * <ul>
 *   <li>密文带 {@link #PREFIX}（"ENC:"）前缀；无前缀视为历史明文，解密时原样返回（迁移/回滚容错）</li>
 *   <li>密钥来自配置 {@code app.pii.key}（≥16 字符），经 SHA-256 派生为 256 位</li>
 *   <li>未配置密钥时降级为明文透传，保证本地无密钥环境可跑通</li>
 * </ul>
 */
public final class PiiCrypto {

    public static final String PREFIX = "ENC:";
    private static final int IV_LEN = 12;
    private static final int TAG_BITS = 128;
    private static final SecureRandom RANDOM = new SecureRandom();

    private PiiCrypto() {
    }

    private static byte[] deriveKey(String secret) {
        try {
            return MessageDigest.getInstance("SHA-256")
                    .digest((secret == null ? "" : secret).getBytes(StandardCharsets.UTF_8));
        } catch (Exception e) {
            throw new IllegalStateException("SHA-256 不可用", e);
        }
    }

    /** 加密；无密钥或空值时不加密（透传），保证弱配置下可用。 */
    public static String encrypt(String plain, String secret) {
        if (plain == null || plain.isEmpty() || secret == null || secret.isEmpty()) {
            return plain;
        }
        try {
            byte[] iv = new byte[IV_LEN];
            RANDOM.nextBytes(iv);
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.ENCRYPT_MODE,
                    new SecretKeySpec(deriveKey(secret), "AES"),
                    new GCMParameterSpec(TAG_BITS, iv));
            byte[] ct = cipher.doFinal(plain.getBytes(StandardCharsets.UTF_8));
            byte[] out = new byte[IV_LEN + ct.length];
            System.arraycopy(iv, 0, out, 0, IV_LEN);
            System.arraycopy(ct, 0, out, IV_LEN, ct.length);
            return PREFIX + Base64.getEncoder().encodeToString(out);
        } catch (Exception e) {
            throw new IllegalStateException("PII 加密失败", e);
        }
    }

    /** 解密；无前缀（历史明文）或空值原样返回。 */
    public static String decrypt(String value, String secret) {
        if (value == null || value.isEmpty() || secret == null || secret.isEmpty()) {
            return value;
        }
        if (!value.startsWith(PREFIX)) {
            return value;
        }
        try {
            byte[] all = Base64.getDecoder().decode(value.substring(PREFIX.length()));
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.DECRYPT_MODE,
                    new SecretKeySpec(deriveKey(secret), "AES"),
                    new GCMParameterSpec(TAG_BITS, all, 0, IV_LEN));
            byte[] pt = cipher.doFinal(all, IV_LEN, all.length - IV_LEN);
            return new String(pt, StandardCharsets.UTF_8);
        } catch (Exception e) {
            throw new IllegalStateException("PII 解密失败（请检查 app.pii.key 是否变更）", e);
        }
    }

    /** 该值是否已加密（带前缀）。 */
    public static boolean isEncrypted(String value) {
        return value != null && value.startsWith(PREFIX);
    }
}
