package com.deliveryhub.user.controller;

import com.nimbusds.jose.jwk.JWKSet;
import com.nimbusds.jose.jwk.RSAKey;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
public class JwksController {

    private final Map<String, Object> publicKeys;

    public JwksController(RSAKey signingKey) {
        this.publicKeys = new JWKSet(signingKey).toJSONObject(true);
    }

    @GetMapping("/.well-known/jwks.json")
    public Map<String, Object> keys() {
        return publicKeys;
    }
}
