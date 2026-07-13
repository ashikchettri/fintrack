package com.fintrack.auth.web;

import com.fintrack.auth.TestcontainersConfiguration;
import com.fintrack.auth.testsupport.RecordingEmailSender;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Full verification lifecycle over HTTP (ADR 004). */
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
class VerificationIntegrationTest {

    private static final String PASSWORD = "correct horse battery staple";

    @Autowired
    private MockMvc mockMvc;

    private void signup(String email) throws Exception {
        mockMvc.perform(post("/api/v1/auth/signup").contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email": "%s", "password": "%s"}""".formatted(email, PASSWORD)))
                .andExpect(status().isCreated());
    }

    private void verify(String email, String code, int expectedStatus) throws Exception {
        mockMvc.perform(post("/api/v1/auth/verify-email").contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email": "%s", "code": "%s"}""".formatted(email, code)))
                .andExpect(status().is(expectedStatus));
    }

    private void login(String email, int expectedStatus) throws Exception {
        mockMvc.perform(post("/api/v1/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email": "%s", "password": "%s"}""".formatted(email, PASSWORD)))
                .andExpect(status().is(expectedStatus));
    }

    @Test
    void signupSendsACodeAndLoginIsBlockedUntilVerified() throws Exception {
        String email = "gate@example.com";
        signup(email);

        String code = RecordingEmailSender.lastCodeFor(email);
        assertThat(code).matches("\\d{6}");

        // correct password, unverified mailbox → distinct 403 problem
        mockMvc.perform(post("/api/v1/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email": "%s", "password": "%s"}""".formatted(email, PASSWORD)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.title").value("Email not verified"));

        verify(email, code, 204);
        login(email, 200);
    }

    @Test
    void wrongCodeIs400AndTheAttemptCapKillsTheCode() throws Exception {
        String email = "cap@example.com";
        signup(email);
        String code = RecordingEmailSender.lastCodeFor(email);
        String wrong = code.equals("000000") ? "111111" : "000000";

        for (int i = 0; i < 5; i++) {
            verify(email, wrong, 400);
        }
        // 5 failures → even the correct code is dead now
        verify(email, code, 400);
        login(email, 403);
    }

    @Test
    void resendReplacesTheCodeAfterCooldownAndIsAlwaysSilent() throws Exception {
        String email = "resend@example.com";
        signup(email);
        String firstCode = RecordingEmailSender.lastCodeFor(email);

        // inside the 60s cooldown: 204 but no new code issued
        mockMvc.perform(post("/api/v1/auth/resend-verification").contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email": "%s"}""".formatted(email)))
                .andExpect(status().isNoContent());
        assertThat(RecordingEmailSender.lastCodeFor(email)).isEqualTo(firstCode);

        // unknown email: identical 204 (no enumeration)
        mockMvc.perform(post("/api/v1/auth/resend-verification").contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email": "ghost-resend@example.com"}"""))
                .andExpect(status().isNoContent());

        // the original code still works
        verify(email, firstCode, 204);
        login(email, 200);
    }

    @Test
    void verifyingTwiceIsIdempotent() throws Exception {
        String email = "twice@example.com";
        signup(email);
        String code = RecordingEmailSender.lastCodeFor(email);

        verify(email, code, 204);
        verify(email, code, 204);  // already verified → still 204
    }

    @Test
    void malformedCodeFailsValidationNotTheService() throws Exception {
        signup("format@example.com");

        mockMvc.perform(post("/api/v1/auth/verify-email").contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email": "format@example.com", "code": "abc123"}"""))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.code").isNotEmpty());
    }
}
