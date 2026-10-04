package com.lucky.user.auth;

import com.lucky.common.auth.HutoolPasswordEncoder;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 密码编码器切换为 Hutool 后的回归测试。
 * <p>
 * 重点不是"新实现能自洽"，而是"旧实现产出的哈希还能不能校验通过" ——
 * 通不过就意味着上线要强制全量重置密码。
 */
class HutoolPasswordEncoderTest {

    private final HutoolPasswordEncoder encoder = new HutoolPasswordEncoder();

    /** 由旧实现（Spring BCryptPasswordEncoder）生成，明文为 myPassw0rd */
    private static final String LEGACY_SPRING_HASH =
            "$2a$10$qwPeYmK.LS1W3OEWQXT2iO9P3KK4f//yrNaYCcrpUtf.JCGUFCfwW";
    private static final String LEGACY_PLAINTEXT = "myPassw0rd";

    @Test
    @DisplayName("存量哈希（旧 Spring 编码器生成）仍能校验通过，无需迁移")
    void verifiesHashProducedByPreviousSpringEncoder() {
        assertThat(encoder.matches(LEGACY_PLAINTEXT, LEGACY_SPRING_HASH)).isTrue();
        assertThat(encoder.matches("wrong-password", LEGACY_SPRING_HASH)).isFalse();
    }

    @Test
    @DisplayName("encode / matches 往返，且同密码两次 encode 因随机盐而不同")
    void encodesAndMatches() {
        String hash = encoder.encode(LEGACY_PLAINTEXT);

        assertThat(hash).hasSize(60).startsWith("$2a$10$");
        assertThat(encoder.matches(LEGACY_PLAINTEXT, hash)).isTrue();
        assertThat(encoder.matches("wrong-password", hash)).isFalse();
        assertThat(encoder.encode(LEGACY_PLAINTEXT)).isNotEqualTo(hash);
    }

    @Test
    @DisplayName("null / 空串 / 畸形哈希一律返回 false，不抛异常")
    void toleratesMalformedHash() {
        assertThat(encoder.matches("whatever", null)).isFalse();
        assertThat(encoder.matches("whatever", "")).isFalse();
        assertThat(encoder.matches("whatever", "not-a-hash")).isFalse();
        assertThat(encoder.matches("whatever", "$2a$10$tooshort")).isFalse();
    }
}
