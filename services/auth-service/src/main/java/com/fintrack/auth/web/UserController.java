package com.fintrack.auth.web;

import com.fintrack.auth.service.ChangePasswordService;
import com.fintrack.auth.service.UserProfileService;
import com.fintrack.auth.web.dto.ChangePasswordRequest;
import com.fintrack.auth.web.dto.UserResponse;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/users")
public class UserController {

    private final UserProfileService userProfileService;
    private final ChangePasswordService changePasswordService;

    public UserController(UserProfileService userProfileService,
                          ChangePasswordService changePasswordService) {
        this.userProfileService = userProfileService;
        this.changePasswordService = changePasswordService;
    }

    /** Identity comes from the verified bearer JWT — no path/body parameters to tamper with. */
    @GetMapping("/me")
    public UserResponse me(@AuthenticationPrincipal Jwt jwt) {
        return UserResponse.from(
                userProfileService.getProfile(UUID.fromString(jwt.getSubject())));
    }

    /** Authenticated password change; requires the current password and revokes other sessions. */
    @PostMapping("/me/password")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void changePassword(@AuthenticationPrincipal Jwt jwt,
                               @Valid @RequestBody ChangePasswordRequest request) {
        changePasswordService.changePassword(
                UUID.fromString(jwt.getSubject()), request.currentPassword(), request.newPassword());
    }
}
