package com.fintrack.auth.web;

import com.fintrack.auth.TestcontainersConfiguration;
import com.fintrack.auth.testsupport.RecordingEmailSender;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Full reset lifecycle over HTTP (ADR 005). */
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
class PasswordResetIntegrationTest {

    private static final String PASSWORD = "correct horse battery staple";
    private static final String NEW_PASSWORD = "completely different secret";

    @Autowired
    private MockMvc mockMvc;

    private void signupVerified(String email) throws Exception {
        mockMvc.perform(post("/api/v1/auth/signup").contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email": "%s", "password": "%s"}""".formatted(email, PASSWORD)))
                .andExpect(status().isCreated());
        mockMvc.perform(post("/api/v1/auth/verify-email").contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email": "%s", "code": "%s"}"""
                                .formatted(email, RecordingEmailSender.lastCodeFor(email))))
                .andExpect(status().isNoContent());
    }

    private MvcResult login(String email, String password, int expectedStatus) throws Exception {
        return mockMvc.perform(post("/api/v1/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email": "%s", "password": "%s"}""".formatted(email, password)))
                .andExpect(status().is(expectedStatus))
                .andReturn();
    }

    private String requestResetCode(String email) throws Exception {
        mockMvc.perform(post("/api/v1/auth/forgot-password").contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email": "%s"}""".formatted(email)))
                .andExpect(status().isNoContent());
        return RecordingEmailSender.lastResetCodeFor(email);
    }

    private void reset(String email, String code, String newPassword, int expectedStatus) throws Exception {
        mockMvc.perform(post("/api/v1/auth/reset-password").contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email": "%s", "code": "%s", "newPassword": "%s"}"""
                                .formatted(email, code, newPassword)))
                .andExpect(status().is(expectedStatus));
    }

    @Test
    void fullResetFlowRotatesThePasswordAndKillsSessions() throws Exception {
        String email = "reset-flow@example.com";
        signupVerified(email);

        // an active session that must die on reset
        String refreshCookie = login(email, PASSWORD, 200)
                .getResponse().getCookie(RefreshTokenCookies.COOKIE_NAME).getValue();

        String code = requestResetCode(email);
        assertThat(code).matches("\\d{6}");
        reset(email, code, NEW_PASSWORD, 204);

        // old password dead, new one works
        login(email, PASSWORD, 401);
        login(email, NEW_PASSWORD, 200);

        // the pre-reset session was revoked (ADR 005)
        mockMvc.perform(post("/api/v1/auth/refresh")
                        .cookie(new Cookie(RefreshTokenCookies.COOKIE_NAME, refreshCookie)))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void resetUnsticksAnUnverifiedAccount() throws Exception {
        String email = "reset-unverified@example.com";
        mockMvc.perform(post("/api/v1/auth/signup").contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email": "%s", "password": "%s"}""".formatted(email, PASSWORD)))
                .andExpect(status().isCreated());
        login(email, PASSWORD, 403);  // unverified

        String code = requestResetCode(email);
        reset(email, code, NEW_PASSWORD, 204);

        // emailed-code entry proved the mailbox — login now works
        login(email, NEW_PASSWORD, 200);
    }

    @Test
    void wrongCodeIs400AndUnknownEmailIsSilentOnRequest() throws Exception {
        String email = "reset-wrong@example.com";
        signupVerified(email);
        String code = requestResetCode(email);
        String wrong = code.equals("000000") ? "111111" : "000000";

        reset(email, wrong, NEW_PASSWORD, 400);
        // old password still works — nothing changed
        login(email, PASSWORD, 200);

        // unknown email: identical 204 (no enumeration)
        mockMvc.perform(post("/api/v1/auth/forgot-password").contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email": "ghost-reset@example.com"}"""))
                .andExpect(status().isNoContent());
    }

    @Test
    void weakNewPasswordFailsValidation() throws Exception {
        mockMvc.perform(post("/api/v1/auth/reset-password").contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email": "x@example.com", "code": "123456", "newPassword": "short"}"""))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.newPassword").isNotEmpty());
    }
}
