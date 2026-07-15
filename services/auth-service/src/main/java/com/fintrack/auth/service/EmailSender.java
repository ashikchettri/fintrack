package com.fintrack.auth.service;

/**
 * Transport seam for outgoing mail: SMTP in real profiles (Mailpit locally,
 * Gmail/app-password interim in deployed envs — ADR 004), a recording fake in
 * tests. Swapping to a transactional provider later touches only the impl.
 */
public interface EmailSender {

    void sendVerificationCode(String toEmail, String code);

    void sendPasswordResetCode(String toEmail, String code);

    void sendEmailChangeCode(String toEmail, String code);

    void sendHouseholdInvite(String toEmail, String inviterName, String householdName, String code);
}
