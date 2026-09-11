package com.lucky.core.config;

import com.lucky.core.interceptor.AuthInterceptor;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * Web MVC 配置：注册鉴权拦截器
 * <p>
 * 为什么必须注册：{@link AuthInterceptor} 上的 {@code @Component} 只是把它变成 Bean，
 * 并不会让它参与请求处理——没有本类，{@code @AuthRequired} 就是个无声的装饰品，
 * 受保护接口会带着 {@code UserContext} 里的 null 正常返回 200，且不报任何错。
 */
@Configuration
public class WebMvcConfig implements WebMvcConfigurer {

    private final AuthInterceptor authInterceptor;

    public WebMvcConfig(AuthInterceptor authInterceptor) {
        this.authInterceptor = authInterceptor;
    }

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        // 拦截所有路径，是否真正校验由 @AuthRequired 决定：
        // AuthInterceptor#preHandle 对未标注该注解的接口直接放行，所以 "/**" 不会误伤
        // 登录/注册/刷新等公开接口，无需在此逐个排除。
        registry.addInterceptor(authInterceptor).addPathPatterns("/**");
    }
}
