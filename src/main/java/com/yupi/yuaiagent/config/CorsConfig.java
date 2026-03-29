package com.yupi.yuaiagent.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * 全局跨域配置。
 * 前后端分离开发时，浏览器会拦截跨域请求，这里统一放行常见配置，避免每个接口单独处理。
 */
@Configuration
public class CorsConfig implements WebMvcConfigurer {

    /**
     * 添加全局 CORS 规则。
     *
     * @param registry Spring MVC 提供的跨域注册器
     */
    @Override
    public void addCorsMappings(CorsRegistry registry) {
        registry.addMapping("/**")
                // 允许带 Cookie（例如会话信息）
                .allowCredentials(true)
                // 允许所有来源。注意：开启 allowCredentials 后不能使用 allowedOrigins("*")，
                // 所以这里用 allowedOriginPatterns("*")。
                .allowedOriginPatterns("*")
                // 放行常见 HTTP 方法
                .allowedMethods("GET", "POST", "PUT", "DELETE", "OPTIONS")
                // 允许所有请求头
                .allowedHeaders("*")
                // 暴露所有响应头给前端读取
                .exposedHeaders("*");
    }
}
