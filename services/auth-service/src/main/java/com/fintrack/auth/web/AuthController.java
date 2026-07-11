package com.fintrack.auth.web;

import com.fintrack.auth.config.JwtProperties;
import com.fintrack.auth.service.InvalidRefreshTokenException;
import com.fintrack.auth.service.LoginResult;
import com.fintrack.auth.service.LoginService;
import com.fintrack.auth.service.RefreshService;
import com.fintrack.auth.service.SignupResult;
import com.fintrack.auth.service.SignupService;
import com.fintrack.auth.web.dto.LoginRequest;
import com.fintrack.auth.web.dto.LoginResponse;
import com.fintrack.auth.web.dto.SignupRequest;
import com.fintrack.auth.web.dto.SignupResponse;
import jakarta.validation.Valid;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.CookieValue;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/auth")
public class AuthController {

    private final SignupService signupService;
    private final LoginService loginService;
    private final RefreshService refreshService;
    private final JwtProperties jwtProperties;
    private final RefreshTokenCookies refreshTokenCookies;

    public AuthController(SignupService signupService,
                          LoginService loginService,
                          RefreshService refreshService,
                          JwtProperties jwtProperties,
                          RefreshTokenCookies refreshTokenCookies) {
        this.signupService = signupService;
        this.loginService = loginService;
        this.refreshService = refreshService;
        this.jwtProperties = jwtProperties;
        this.refreshTokenCookies = refreshTokenCookies;
    }

    @PostMapping("/signup")
    @ResponseStatus(HttpStatus.CREATED)
    public SignupResponse signup(@Valid @RequestBody SignupRequest request) {
        SignupResult result = signupService.signup(request.email(), request.password());
        return SignupResponse.from(result);
    }

    @PostMapping("/login")
    public ResponseEntity<LoginResponse> login(@Valid @RequestBody LoginRequest request) {
        LoginResult result = loginService.login(request.email(), request.password());
        return withRotatedCookie(result);
    }

    @PostMapping("/refresh")
    public ResponseEntity<LoginResponse> refresh(
            @CookieValue(name = RefreshTokenCookies.COOKIE_NAME, required = false) String refreshToken) {
        if (refreshToken == null) {
            // missing cookie and invalid token are the same generic 401
            throw new InvalidRefreshTokenException();
        }
        return withRotatedCookie(refreshService.refresh(refreshToken));
    }

    @PostMapping("/logout")
    public ResponseEntity<Void> logout(
            @CookieValue(name = RefreshTokenCookies.COOKIE_NAME, required = false) String refreshToken) {
        if (refreshToken != null) {
            refreshService.logout(refreshToken);
        }
        // always clear the cookie and always 204 — logout never fails
        return ResponseEntity.noContent()
                .header(HttpHeaders.SET_COOKIE, refreshTokenCookies.expire().toString())
                .build();
    }

    private ResponseEntity<LoginResponse> withRotatedCookie(LoginResult result) {
        return ResponseEntity.ok()
                .header(HttpHeaders.SET_COOKIE, refreshTokenCookies.issue(result.refreshToken()).toString())
                .body(LoginResponse.from(result, jwtProperties.accessTokenTtl().toSeconds()));
    }
}
