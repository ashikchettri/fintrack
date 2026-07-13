package com.fintrack.auth.service;

import java.time.Duration;

/**
 * One place for outgoing email copy, shared by every EmailSender impl.
 * Plain-text always; HTML variant (branded code block) used by transports
 * that support it (Resend).
 */
public record EmailContent(String subject, String text, String html) {

    public static EmailContent verificationCode(String code, Duration ttl) {
        return new EmailContent(
                "Your FinTrack verification code",
                """
                Your FinTrack verification code is: %s

                It expires in %d minutes. If you didn't sign up, ignore this email.
                """.formatted(code, ttl.toMinutes()),
                html("Verify your email",
                        "Enter this code in FinTrack to finish creating your account.",
                        code, ttl));
    }

    public static EmailContent passwordResetCode(String code, Duration ttl) {
        return new EmailContent(
                "Your FinTrack password reset code",
                """
                Your FinTrack password reset code is: %s

                It expires in %d minutes. If you didn't request a reset, you can
                ignore this email — your password is unchanged.
                """.formatted(code, ttl.toMinutes()),
                html("Reset your password",
                        "Enter this code in FinTrack to set a new password.",
                        code, ttl));
    }

    public static EmailContent emailChangeCode(String code, Duration ttl) {
        return new EmailContent(
                "Confirm your new FinTrack email",
                """
                Your FinTrack email-change code is: %s

                Enter it in FinTrack to confirm this address. It expires in %d
                minutes. If you didn't request this, ignore this email.
                """.formatted(code, ttl.toMinutes()),
                html("Confirm your new email",
                        "Enter this code in FinTrack to confirm your new email address.",
                        code, ttl));
    }

    private static String html(String title, String intro, String code, Duration ttl) {
        return """
                <div style="font-family:-apple-system,Segoe UI,Roboto,sans-serif;max-width:420px;margin:0 auto;padding:24px">
                  <h2 style="color:#0f766e;margin:0 0 8px">%s</h2>
                  <p style="color:#555;font-size:15px;margin:0 0 20px">%s</p>
                  <div style="font-size:36px;font-weight:700;letter-spacing:10px;color:#0f766e;
                              background:#f0fdfa;border-radius:12px;padding:16px;text-align:center">%s</div>
                  <p style="color:#999;font-size:13px;margin:20px 0 0">
                    This code expires in %d minutes. If you didn't request it, you can ignore this email.
                  </p>
                </div>
                """.formatted(title, intro, code, ttl.toMinutes());
    }
}
