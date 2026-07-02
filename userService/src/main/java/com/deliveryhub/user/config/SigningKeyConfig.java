package com.deliveryhub.user.config;

import com.deliveryhub.user.security.JwtProperties;
import com.deliveryhub.user.security.RsaKeyPairs;
import com.nimbusds.jose.JOSEException;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.jwk.JWKSet;
import com.nimbusds.jose.jwk.KeyUse;
import com.nimbusds.jose.jwk.RSAKey;
import com.nimbusds.jose.jwk.source.ImmutableJWKSet;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;

import java.security.KeyPair;
import java.security.interfaces.RSAPrivateKey;
import java.security.interfaces.RSAPublicKey;

@Configuration
@EnableConfigurationProperties(JwtProperties.class)
public class SigningKeyConfig {

    private static final Logger log = LoggerFactory.getLogger(SigningKeyConfig.class);

    @Bean
    public RSAKey signingKey(JwtProperties properties) {
        KeyPair keyPair = keyPair(properties);
        try {
            RSAKey signingKey = new RSAKey.Builder((RSAPublicKey) keyPair.getPublic())
                    .privateKey((RSAPrivateKey) keyPair.getPrivate())
                    .keyUse(KeyUse.SIGNATURE)
                    .algorithm(JWSAlgorithm.RS256)
                    .keyIDFromThumbprint()
                    .build();
            log.info("JWT signing key is ready, kid={}", signingKey.getKeyID());
            return signingKey;
        } catch (JOSEException e) {
            throw new IllegalStateException("Key id cannot be derived from the signing key", e);
        }
    }

    @Bean
    public JwtEncoder jwtEncoder(RSAKey signingKey) {
        return new NimbusJwtEncoder(new ImmutableJWKSet<>(new JWKSet(signingKey)));
    }

    private static KeyPair keyPair(JwtProperties properties) {
        if (properties.hasPrivateKey()) {
            return RsaKeyPairs.fromPkcs8(properties.privateKey());
        }
        log.warn("app.jwt.private-key is not set: generated a temporary RSA key, "
                + "issued tokens stop being valid after restart");
        return RsaKeyPairs.generate();
    }
}
