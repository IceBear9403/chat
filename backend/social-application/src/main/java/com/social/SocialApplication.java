package com.social;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * 启动类。
 *
 * <p>dev 环境：{@code ./mvnw -pl social-application -am spring-boot:run -Dspring-boot.run.profiles=dev}
 */
@SpringBootApplication
@MapperScan("com.social.service.mapper")
@EnableScheduling
public class SocialApplication {

    public static void main(String[] args) {
        SpringApplication.run(SocialApplication.class, args);
    }
}
