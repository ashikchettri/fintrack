package com.fintrack.auth.web;

import com.fintrack.auth.service.SignupResult;
import com.fintrack.auth.service.SignupService;
import com.fintrack.auth.web.dto.SignupRequest;
import com.fintrack.auth.web.dto.SignupResponse;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/auth")
public class AuthController {

    private final SignupService signupService;

    public AuthController(SignupService signupService) {
        this.signupService = signupService;
    }

    @PostMapping("/signup")
    @ResponseStatus(HttpStatus.CREATED)
    public SignupResponse signup(@Valid @RequestBody SignupRequest request) {
        SignupResult result = signupService.signup(request.email(), request.password());
        return SignupResponse.from(result);
    }
}