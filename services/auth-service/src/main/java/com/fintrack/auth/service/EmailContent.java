package com.fintrack.auth.service;

import java.time.Duration;

/** One place for outgoing email copy, shared by every EmailSender impl. */
public record EmailContent(String subject, String text) {

    public static EmailContent verificationCode(String code, Duration ttl) {
        return new EmailContent("Your FinTrack verification code", """
                Your FinTrack verification code is: %s

                It expires in %d minutes. If you didn't sign up, ignore this email.
                """.formatted(code, ttl.toMinutes()));
    }

    public static EmailContent passwordResetCode(String code, Duration ttl) {
        return new EmailContent("Your FinTrack password reset code", """
                Your FinTrack password reset code is: %s

                It expires in %d minutes. If you didn't request a reset, you can
                ignore this email — your password is unchanged.
                """.formatted(code, ttl.toMinutes()));
    }
}
