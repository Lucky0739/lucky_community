package com.lucky.common.autoconfigure;

import com.lucky.common.auth.JwtUtil;
import com.lucky.common.auth.TokenRedisService;
import com.lucky.common.auth.interceptor.AuthInterceptor;
import com.lucky.common.exception.GlobalExceptionHandler;
import com.lucky.common.file.FileService;
import io.minio.MinioClient;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.boot.autoconfigure.data.redis.RedisAutoConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.web.servlet.DispatcherServlet;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * 公共自动配置。按职责拆成内部类，是为了让条件注解各自生效：
 * JWT 不依赖 Redis，认证依赖 Redis，拦截器注册依赖认证 Bean，MinIO 依赖端点配置。
 */
public final class LuckyAutoConfiguration {

    private LuckyAutoConfiguration() {
    }

    @AutoConfiguration
    @ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
    @ConditionalOnClass(DispatcherServlet.class)
    public static class Web {

        @Bean
        @ConditionalOnMissingBean
        public GlobalExceptionHandler globalExceptionHandler() {
            return new GlobalExceptionHandler();
        }
    }

    /** 不依赖 Redis，单独注册，避免和 Redis 自动配置抢顺序。 */
    @AutoConfiguration
    public static class Jwt {

        @Bean
        @ConditionalOnMissingBean
        public JwtUtil jwtUtil() {
            return new JwtUtil();
        }
    }

    @AutoConfiguration(after = {Jwt.class, RedisAutoConfiguration.class})
    @ConditionalOnClass(StringRedisTemplate.class)
    @ConditionalOnBean(StringRedisTemplate.class)
    public static class Auth {

        @Bean
        @ConditionalOnMissingBean
        public TokenRedisService tokenRedisService(StringRedisTemplate stringRedisTemplate) {
            return new TokenRedisService(stringRedisTemplate);
        }

        @Bean
        @ConditionalOnMissingBean
        public AuthInterceptor authInterceptor(JwtUtil jwtUtil, TokenRedisService tokenRedisService) {
            return new AuthInterceptor(jwtUtil, tokenRedisService);
        }
    }

    @AutoConfiguration(after = {Jwt.class, Auth.class})
    @ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
    @ConditionalOnBean(AuthInterceptor.class)
    @ConditionalOnProperty(prefix = "lucky.auth", name = "interceptor-enabled", matchIfMissing = true)
    public static class AuthWeb implements WebMvcConfigurer {

        private final AuthInterceptor authInterceptor;

        public AuthWeb(AuthInterceptor authInterceptor) {
            this.authInterceptor = authInterceptor;
        }

        @Override
        public void addInterceptors(InterceptorRegistry registry) {
            registry.addInterceptor(authInterceptor).addPathPatterns("/**").order(1);
        }
    }

    @AutoConfiguration
    @ConditionalOnClass(MinioClient.class)
    @ConditionalOnProperty(prefix = "minio", name = "endpoint")
    public static class Minio {
        @Bean
        @ConditionalOnMissingBean
        public MinioClient minioClient(@Value("${minio.endpoint}") String endpoint,
                                       @Value("${minio.access-key}") String accessKey,
                                       @Value("${minio.secret-key}") String secretKey) {
            return MinioClient.builder()
                    .endpoint(endpoint)
                    .credentials(accessKey, secretKey)
                    .build();
        }

        @Bean
        @ConditionalOnMissingBean
        public FileService fileService(MinioClient minioClient) {
            return new FileService(minioClient);
        }
    }
}
