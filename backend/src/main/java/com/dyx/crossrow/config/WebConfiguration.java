package com.dyx.crossrow.config;

import com.dyx.crossrow.interceptor.JwtInterceptor;
import jakarta.annotation.Resource;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
public class WebConfiguration implements WebMvcConfigurer {

    @Resource
    private JwtInterceptor jwtInterceptor;
    @Override
    public void addCorsMappings(CorsRegistry registry) {
        registry.addMapping("/**")
                .allowedOrigins(
                        "http://localhost:5173",
                        "http://localhost:80",
                        "http://localhost",
                        "http://47.236.147.135",
                        "http://47.236.147.135:80",
                        "https://c4rows.com",
                        "https://www.c4rows.com"
                )
                .allowedMethods("GET", "POST", "PUT", "DELETE", "OPTIONS")
                .allowedHeaders("*")
                .allowCredentials(true);
    }

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(jwtInterceptor)
                .addPathPatterns("/**") // 拦截所有请求
                // 下面是“白名单”，保安看到这些路径直接放行
                .excludePathPatterns(
                        "/auth/login",
                        "/auth/register",
                        "/doc.html",            // Knife4j 文档主页
                        "/swagger-ui/**",       // Swagger UI
                        "/v3/api-docs/**",      // OpenAPI 数据
                        "/webjars/**",          // 静态资源
                        "/images/**"            // 静态图片资源 (context-path /api 下)
                );
    }
}
