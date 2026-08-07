package com.social.api.config;

import com.social.api.interceptor.AuthInterceptor;
import com.social.service.config.AppProperties;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * Web MVC 配置：跨域 + 认证拦截器 + 本地文件静态映射。
 */
@Configuration
@RequiredArgsConstructor
public class WebConfig implements WebMvcConfigurer {

    private final AuthInterceptor authInterceptor;
    private final AppProperties appProperties;

    /** 允许的跨域来源，application.yml 配置，开发默认 http://localhost:5173 */
    @Value("${app.cors.allowed-origins:http://localhost:5173}")
    private String[] allowedOrigins;

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(authInterceptor)
                .addPathPatterns("/api/**")
                .excludePathPatterns(
                        "/api/auth/**",      // 认证接口免登录
                        "/error");
    }

    @Override
    public void addCorsMappings(CorsRegistry registry) {
        registry.addMapping("/**")
                .allowedOrigins(allowedOrigins)
                .allowedMethods("GET", "POST", "PUT", "DELETE", "OPTIONS")
                .allowedHeaders("*")
                .exposedHeaders("Authorization")
                .allowCredentials(true)
                .maxAge(3600);
    }

    /**
     * 本地文件存储静态映射：/uploads/** → {app.storage.local-path}/
     * 生产切 OSS 后，上传文件走 CDN 域名，此映射可移除。
     */
    @Override
    public void addResourceHandlers(ResourceHandlerRegistry registry) {
        String localPath = appProperties.getStorage().getLocalPath();
        String location = localPath.endsWith("/") ? "file:" + localPath : "file:" + localPath + "/";
        registry.addResourceHandler("/uploads/**").addResourceLocations(location);
    }
}
