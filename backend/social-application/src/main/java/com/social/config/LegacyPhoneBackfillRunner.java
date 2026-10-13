package com.social.config;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.social.common.util.DigestUtil;
import com.social.common.util.PiiCrypto;
import com.social.service.config.AppProperties;
import com.social.service.entity.User;
import com.social.service.mapper.UserMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * 启动回填：阶段七之前的老用户手机号为明文，现改为 AES-GCM 加密 + phone_hash 查重。
 * 启动时将明文 phone 加密并回填 phone_hash（幂等：只处理 phone_hash 为空且 phone 非空的行）。
 * <p>分批处理避免大表一次性加载；迁移 SQL 见 docs/sql/migration_phase7_compliance.sql。
 */
@Slf4j
@Component
@Order(1)
@RequiredArgsConstructor
public class LegacyPhoneBackfillRunner implements ApplicationRunner {

    private final UserMapper userMapper;
    private final AppProperties properties;

    @Override
    public void run(ApplicationArguments args) {
        String key = properties.getPii().getKey();
        if (key == null || key.isBlank()) {
            log.warn("[backfill] app.pii.key 未配置，跳过手机号加密回填（生产必须配置 APP_PII_KEY）");
            return;
        }
        int total = 0;
        long fromId = 0;
        for (int batch = 0; batch < 1000; batch++) {
            List<User> users = userMapper.selectList(new LambdaQueryWrapper<User>()
                    .isNull(User::getPhoneHash)
                    .isNotNull(User::getPhone)
                    .gt(User::getId, fromId)
                    .orderByAsc(User::getId)
                    .last("LIMIT 500"));
            if (users.isEmpty()) {
                break;
            }
            for (User user : users) {
                String phone = user.getPhone();
                String hash = DigestUtil.sha256Hex(PiiCrypto.decrypt(phone, key));
                if (!PiiCrypto.isEncrypted(phone)) {
                    // 明文老数据 → 加密存储
                    user.setPhone(PiiCrypto.encrypt(phone, key));
                }
                user.setPhoneHash(hash);
                userMapper.updateById(user);
                total++;
            }
            fromId = users.get(users.size() - 1).getId();
            if (users.size() < 500) {
                break;
            }
        }
        if (total > 0) {
            log.info("[backfill] 完成 {} 条手机号加密回填", total);
        }
    }
}
