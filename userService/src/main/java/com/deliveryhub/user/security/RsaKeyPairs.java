package com.deliveryhub.user.security;

import java.security.GeneralSecurityException;
import java.security.KeyFactory;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.security.interfaces.RSAPrivateCrtKey;
import java.security.spec.PKCS8EncodedKeySpec;
import java.security.spec.RSAPublicKeySpec;
import java.util.Base64;

public final class RsaKeyPairs {

    private static final String ALGORITHM = "RSA";
    private static final int KEY_SIZE_BITS = 2048;

    private RsaKeyPairs() {
    }

    public static KeyPair generate() {
        try {
            KeyPairGenerator generator = KeyPairGenerator.getInstance(ALGORITHM);
            generator.initialize(KEY_SIZE_BITS);
            return generator.generateKeyPair();
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("RSA key pair cannot be generated", e);
        }
    }

    public static KeyPair fromPkcs8(String pem) {
        try {
            byte[] der = Base64.getDecoder().decode(pem
                    .replaceAll("-----(BEGIN|END) PRIVATE KEY-----", "")
                    .replace("\\n", "")
                    .replaceAll("\\s", ""));
            KeyFactory keyFactory = KeyFactory.getInstance(ALGORITHM);
            PrivateKey privateKey = keyFactory.generatePrivate(new PKCS8EncodedKeySpec(der));
            if (!(privateKey instanceof RSAPrivateCrtKey crtKey)) {
                throw new IllegalStateException("app.jwt.private-key must be an RSA key with CRT parameters");
            }
            PublicKey publicKey = keyFactory.generatePublic(
                    new RSAPublicKeySpec(crtKey.getModulus(), crtKey.getPublicExponent()));
            return new KeyPair(publicKey, privateKey);
        } catch (GeneralSecurityException | IllegalArgumentException e) {
            throw new IllegalStateException("app.jwt.private-key is not a PKCS#8 RSA private key in PEM", e);
        }
    }
}
