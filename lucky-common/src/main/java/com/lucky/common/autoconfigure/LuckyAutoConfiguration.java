package com.lucky.common.autoconfigure;

import com.lucky.common.auth.HutoolPasswordEncoder;
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
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.servlet.DispatcherServlet;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import java.time.Clock;

/**
 * 公共自动配置。按职责拆成内部类，是为了让条件注解各自生效：
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

    /**
     * 密码编码器由 Hutool BCrypt 实现。spring-security-crypto 在本模块是 optional 依赖，
     */
    @AutoConfiguration
    @ConditionalOnClass(PasswordEncoder.class)
    public static class Password {

        @Bean
        @ConditionalOnMissingBean
        public PasswordEncoder passwordEncoder() {
            return new HutoolPasswordEncoder();
        }
    }

    /**
     * 时间源。需要「当前时刻」的地方注入 {@link Clock}，不要在业务代码里直接调
     * {@code LocalDateTime.now()}：后者用 JVM 默认时区且不可替换，单测里没法把时间钉死
     * （注入后可以用 {@code Clock.fixed(...)} 断言写进去的时间）。将来统一换时区也只改这一处。
     */
    @AutoConfiguration
    public static class Time {

        @Bean
        @ConditionalOnMissingBean
        public Clock clock() {
            return Clock.systemDefaultZone();
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
