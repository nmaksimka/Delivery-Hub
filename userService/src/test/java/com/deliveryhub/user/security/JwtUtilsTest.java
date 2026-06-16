package com.deliveryhub.user.security;

import com.deliveryhub.user.entity.Role;
import com.deliveryhub.user.entity.User;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DisplayName("JwtUtils")
class JwtUtilsTest {

    private static final String SECRET = "test-secret-key-that-is-long-enough-for-hs256";

    private JwtUtils jwtUtils;

    @BeforeEach
    void setUp() {
        jwtUtils = new JwtUtils(SECRET, 60_000L);
    }

    @Test
    @DisplayName("кладёт в токен id и роль, чтобы гейтвей не ходил в БД")
    void putsUserIdAndRoleIntoClaims() {
        String token = jwtUtils.generateToken(user(Role.COURIER));

        Claims claims = parse(token);
        assertThat(claims.getSubject()).isEqualTo("courier@example.com");
        assertThat(claims.get(JwtUtils.CLAIM_USER_ID, Number.class).longValue()).isEqualTo(7L);
        assertThat(claims.get(JwtUtils.CLAIM_ROLE, String.class)).isEqualTo("COURIER");
    }

    @Test
    @DisplayName("выданный токен проходит собственную валидацию")
    void validatesOwnToken() {
        assertThat(jwtUtils.validateToken(jwtUtils.generateToken(user(Role.USER)))).isTrue();
    }

    @Test
    @DisplayName("отвергает токен, подписанный другим ключом")
    void rejectsForeignSignature() {
        SecretKey otherKey = Keys.hmacShaKeyFor(
                "completely-different-secret-key-32-bytes-min".getBytes(StandardCharsets.UTF_8));
        String forged = Jwts.builder().subject("attacker@example.com").signWith(otherKey).compact();

        assertThat(jwtUtils.validateToken(forged)).isFalse();
    }

    @Test
    @DisplayName("отвергает мусор вместо токена")
    void rejectsGarbage() {
        assertThat(jwtUtils.validateToken("not-a-jwt")).isFalse();
        assertThat(jwtUtils.validateToken("")).isFalse();
    }

    @Test
    @DisplayName("отвергает просроченный токен")
    void rejectsExpiredToken() throws InterruptedException {
        JwtUtils shortLived = new JwtUtils(SECRET, 1L);
        String token = shortLived.generateToken(user(Role.USER));

        Thread.sleep(1_100);

        assertThat(shortLived.validateToken(token)).isFalse();
    }

    @Test
    @DisplayName("не стартует со слишком коротким секретом")
    void rejectsWeakSecret() {
        assertThatThrownBy(() -> new JwtUtils("short", 60_000L))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("32 bytes");
    }

    private Claims parse(String token) {
        SecretKey key = Keys.hmacShaKeyFor(SECRET.getBytes(StandardCharsets.UTF_8));
        return Jwts.parser().verifyWith(key).build().parseSignedClaims(token).getPayload();
    }

    private static User user(Role role) {
        return User.builder()
                .id(7L)
                .email(role == Role.COURIER ? "courier@example.com" : "user@example.com")
                .name("Test")
                .role(role)
                .build();
    }
}
