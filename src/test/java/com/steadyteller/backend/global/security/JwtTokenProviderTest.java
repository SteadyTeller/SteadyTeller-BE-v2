package com.steadyteller.backend.global.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.jsonwebtoken.JwtException;
import java.util.Base64;
import org.junit.jupiter.api.Test;

class JwtTokenProviderTest {

    private static final String SECRET = Base64.getEncoder()
            .encodeToString("steadyteller-jwt-test-secret-key-2026".getBytes());

    @Test
    void createsAndParsesAccessToken() {
        JwtTokenProvider tokenProvider = new JwtTokenProvider(SECRET, 3_600_000L);

        String token = tokenProvider.createAccessToken(42L);

        assertThat(tokenProvider.getMemberId(token)).isEqualTo(42L);
        assertThat(tokenProvider.getAccessTokenExpirationSeconds()).isEqualTo(3600L);
    }

    @Test
    void rejectsTokenSignedWithDifferentSecret() {
        JwtTokenProvider issuer = new JwtTokenProvider(SECRET, 3_600_000L);
        String anotherSecret = Base64.getEncoder()
                .encodeToString("another-jwt-test-secret-key-2026-xx".getBytes());
        JwtTokenProvider verifier = new JwtTokenProvider(anotherSecret, 3_600_000L);

        String token = issuer.createAccessToken(42L);

        assertThatThrownBy(() -> verifier.getMemberId(token))
                .isInstanceOf(JwtException.class);
    }
}
