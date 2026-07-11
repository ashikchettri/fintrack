package com.fintrack.auth.web;

import com.nimbusds.jose.jwk.JWKSet;
import com.nimbusds.jose.jwk.RSAKey;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * Public keys for token verification (RFC 7517). Downstream services fetch
 * this instead of sharing a secret — the whole reason we sign RS256, not HS256.
 * Serves a key LIST so rotation later means "publish old + new" with no
 * contract change (ADR 002).
 */
@RestController
public class JwksController {

    private final Map<String, Object> jwks;

    public JwksController(RSAKey rsaSigningKey) {
        // toPublicJWK() strips the private parameters; computed once — the key
        // set is immutable for the lifetime of the process
        this.jwks = new JWKSet(rsaSigningKey.toPublicJWK()).toJSONObject();
    }

    @GetMapping("/.well-known/jwks.json")
    public Map<String, Object> keys() {
        return jwks;
    }
}
