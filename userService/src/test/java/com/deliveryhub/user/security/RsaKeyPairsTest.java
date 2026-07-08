package com.deliveryhub.user.security;

import org.junit.jupiter.api.Test;

import java.security.KeyPair;
import java.util.Base64;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class RsaKeyPairsTest {

    private static final KeyPair ORIGINAL = RsaKeyPairs.generate();

    @Test
    void readsPemAndDerivesPublicKeyFromPrivate() {
        String body = Base64.getMimeEncoder(64, "\n".getBytes()).encodeToString(ORIGINAL.getPrivate().getEncoded());
        String pem = "-----BEGIN PRIVATE KEY-----\n" + body + "\n-----END PRIVATE KEY-----\n";

        KeyPair restored = RsaKeyPairs.fromPkcs8(pem);

        assertThat(restored.getPrivate()).isEqualTo(ORIGINAL.getPrivate());
        assertThat(restored.getPublic()).isEqualTo(ORIGINAL.getPublic());
    }

    @Test
    void readsSingleLineValueFromEnvironmentVariable() {
        String oneLine = Base64.getEncoder().encodeToString(ORIGINAL.getPrivate().getEncoded());

        assertThat(RsaKeyPairs.fromPkcs8(oneLine).getPublic()).isEqualTo(ORIGINAL.getPublic());
    }

    @Test
    void readsPemWithEscapedLineBreaks() {
        String body = Base64.getEncoder().encodeToString(ORIGINAL.getPrivate().getEncoded());
        String escaped = "-----BEGIN PRIVATE KEY-----\\n" + body + "\\n-----END PRIVATE KEY-----";

        assertThat(RsaKeyPairs.fromPkcs8(escaped).getPrivate()).isEqualTo(ORIGINAL.getPrivate());
    }

    @Test
    void rejectsValueThatIsNotKey() {
        assertThatThrownBy(() -> RsaKeyPairs.fromPkcs8("not-a-key"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("app.jwt.private-key");
    }

    @Test
    void generatesDistinctKeys() {
        assertThat(RsaKeyPairs.generate().getPublic()).isNotEqualTo(ORIGINAL.getPublic());
    }
}
