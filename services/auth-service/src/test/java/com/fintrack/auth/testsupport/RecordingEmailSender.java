package com.fintrack.auth.testsupport;

import com.fintrack.auth.service.EmailSender;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Test seam for outgoing mail: captures codes instead of sending. Static
 * store so Karate features can fetch codes via Java interop
 * (Java.type(...).lastCodeFor(email)).
 */
public class RecordingEmailSender implements EmailSender {

    private static final Map<String, String> CODES_BY_EMAIL = new ConcurrentHashMap<>();
    private static final Map<String, String> RESET_CODES_BY_EMAIL = new ConcurrentHashMap<>();

    @Override
    public void sendVerificationCode(String toEmail, String code) {
        CODES_BY_EMAIL.put(toEmail, code);
    }

    @Override
    public void sendPasswordResetCode(String toEmail, String code) {
        RESET_CODES_BY_EMAIL.put(toEmail, code);
    }

    public static String lastCodeFor(String email) {
        return CODES_BY_EMAIL.get(email);
    }

    public static String lastResetCodeFor(String email) {
        return RESET_CODES_BY_EMAIL.get(email);
    }
}
