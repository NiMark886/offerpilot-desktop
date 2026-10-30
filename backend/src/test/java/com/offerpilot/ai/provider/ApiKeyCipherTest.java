package com.offerpilot.ai.provider;

import com.offerpilot.ai.provider.service.ApiKeyCipher;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class ApiKeyCipherTest {

    private final ApiKeyCipher cipher = new ApiKeyCipher("", "unit-test-jwt-secret");

    @Test
    void encryptsAndDecryptsWithoutLeakingPlaintext() {
        String plaintext = "sk-1234567890abcdef";

        String encrypted = cipher.encrypt(plaintext);

        assertThat(encrypted).isNotEqualTo(plaintext);
        assertThat(encrypted).doesNotContain(plaintext);
        assertThat(cipher.decrypt(encrypted)).isEqualTo(plaintext);
    }

    @Test
    void producesDifferentCiphertextForSamePlaintext() {
        String plaintext = "sk-1234567890abcdef";

        assertThat(cipher.encrypt(plaintext)).isNotEqualTo(cipher.encrypt(plaintext));
    }

    @Test
    void masksApiKeys() {
        assertThat(cipher.mask("sk-1234567890abcd")).isEqualTo("sk-****abcd");
        assertThat(cipher.mask("short")).isEqualTo("****");
        assertThat(cipher.mask("")).isEmpty();
    }
}
