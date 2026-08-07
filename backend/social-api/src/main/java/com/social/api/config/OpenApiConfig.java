package com.social.api.config;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * OpenAPI（Swagger）配置：接口文档地址 /swagger-ui.html，接入 JWT 认证说明。
 */
@Configuration
public class OpenApiConfig {

    @Bean
    public OpenAPI socialOpenAPI() {
        return new OpenAPI()
                .info(new Info()
                        .title("Social API")
                        .description("H5 社交网站后端接口文档")
                        .version("0.1.0"))
                .addSecurityItem(new SecurityRequirement().addList("BearerAuth"))
                .components(new Components().addSecuritySchemes("BearerAuth",
                        new SecurityScheme()
                                .name("Authorization")
                                .type(SecurityScheme.Type.HTTP)
                                .scheme("bearer")
                                .bearerFormat("JWT")));
    }
}
