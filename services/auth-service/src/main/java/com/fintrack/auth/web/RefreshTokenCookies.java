package com.fintrack.auth.web;

import com.fintrack.auth.config.JwtProperties;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseCookie;
import org.springframework.stereotype.Component;

/**
 * The refresh token's only transport (ADR 003):
 * httpOnly — JS can never read it (XSS-proof);
 * SameSite=Strict — never attached cross-site, which is also the CSRF defense;
 * Path-scoped — sent only to the auth endpoints, not every API call;
 * Secure — except in plain-http local dev.
 */
@Component
public class RefreshTokenCookies {

    public static final String COOKIE_NAME = "fintrack_refresh";
    private static final String PATH = "/api/v1/auth";

    private final JwtProperties jwtProperties;
    private final boolean secure;

    public RefreshTokenCookies(JwtProperties jwtProperties,
                               @Value("${fintrack.auth.refresh-cookie.secure:true}") boolean secure) {
        this.jwtProperties = jwtProperties;
        this.secure = secure;
    }

    public ResponseCookie issue(String rawRefreshToken) {
        return ResponseCookie.from(COOKIE_NAME, rawRefreshToken)
                .httpOnly(true)
                .secure(secure)
                .sameSite("Strict")
                .path(PATH)
                .maxAge(jwtProperties.refreshTokenTtl())
                .build();
    }

    /** Max-Age=0 tells the browser to delete the cookie (logout). */
    public ResponseCookie expire() {
        return ResponseCookie.from(COOKIE_NAME, "")
                .httpOnly(true)
                .secure(secure)
                .sameSite("Strict")
                .path(PATH)
                .maxAge(0)
                .build();
    }
}
