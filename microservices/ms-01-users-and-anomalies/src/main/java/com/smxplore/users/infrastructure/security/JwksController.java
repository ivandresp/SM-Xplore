package com.smxplore.users.infrastructure.security;

import com.nimbusds.jose.jwk.RSAKey;
import java.util.List;
import java.util.Map;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class JwksController {

    private final RSAKey rsaKey;

    public JwksController(RSAKey rsaKey) {
        this.rsaKey = rsaKey;
    }

    @GetMapping("/.well-known/jwks.json")
    public Map<String, Object> jwks() {
        return Map.of("keys", List.of(rsaKey.toPublicJWK().toJSONObject()));
    }
}
