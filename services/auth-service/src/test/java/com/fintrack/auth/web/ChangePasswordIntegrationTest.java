package com.fintrack.auth.web;

import com.fintrack.auth.TestcontainersConfiguration;
import com.fintrack.auth.testsupport.RecordingEmailSender;
import com.jayway.jsonpath.JsonPath;
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
import static org.springframework.http.HttpHeaders.AUTHORIZATION;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
class ChangePasswordIntegrationTest {

    private static final String PASSWORD = "correct horse battery staple";
    private static final String NEW_PASSWORD = "an entirely new secret";

    @Autowired
    private MockMvc mockMvc;

    private record Session(String accessToken, String refreshCookie) {
    }

    private Session signupVerifyLogin(String email) throws Exception {
        mockMvc.perform(post("/api/v1/auth/signup").contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email": "%s", "password": "%s"}""".formatted(email, PASSWORD)))
                .andExpect(status().isCreated());
        mockMvc.perform(post("/api/v1/auth/verify-email").contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email": "%s", "code": "%s"}"""
                                .formatted(email, RecordingEmailSender.lastCodeFor(email))))
                .andExpect(status().isNoContent());
        MvcResult result = mockMvc.perform(post("/api/v1/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email": "%s", "password": "%s"}""".formatted(email, PASSWORD)))
                .andExpect(status().isOk())
                .andReturn();
        return new Session(
                JsonPath.read(result.getResponse().getContentAsString(), "$.accessToken"),
                result.getResponse().getCookie(RefreshTokenCookies.COOKIE_NAME).getValue());
    }

    @Test
    void changePasswordRotatesTheHashAndRevokesSessions() throws Exception {
        Session session = signupVerifyLogin("change-pw@example.com");

        mockMvc.perform(post("/api/v1/users/me/password")
                        .header(AUTHORIZATION, "Bearer " + session.accessToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"currentPassword": "%s", "newPassword": "%s"}"""
                                .formatted(PASSWORD, NEW_PASSWORD)))
                .andExpect(status().isNoContent());

        // old password no longer logs in; new one does
        mockMvc.perform(post("/api/v1/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email": "change-pw@example.com", "password": "%s"}""".formatted(PASSWORD)))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(post("/api/v1/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email": "change-pw@example.com", "password": "%s"}""".formatted(NEW_PASSWORD)))
                .andExpect(status().isOk());

        // the pre-change refresh session was revoked
        mockMvc.perform(post("/api/v1/auth/refresh")
                        .cookie(new Cookie(RefreshTokenCookies.COOKIE_NAME, session.refreshCookie())))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void wrongCurrentPasswordIs400WithTraceId() throws Exception {
        Session session = signupVerifyLogin("change-pw-wrong@example.com");

        mockMvc.perform(post("/api/v1/users/me/password")
                        .header(AUTHORIZATION, "Bearer " + session.accessToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"currentPassword": "not-my-password", "newPassword": "%s"}"""
                                .formatted(NEW_PASSWORD)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.title").value("Incorrect current password"))
                // the correlation id is stamped into the problem body (and header)
                .andExpect(jsonPath("$.traceId").isNotEmpty())
                .andExpect(header().exists("X-Request-Id"));
    }

    @Test
    void changePasswordRequiresAuthentication() throws Exception {
        mockMvc.perform(post("/api/v1/users/me/password").contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"currentPassword": "x", "newPassword": "an entirely new secret"}"""))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void inboundRequestIdIsEchoedBack() throws Exception {
        MvcResult result = mockMvc.perform(get("/actuator/health")
                        .header("X-Request-Id", "client-supplied-trace-1234"))
                .andExpect(status().isOk())
                .andReturn();
        assertThat(result.getResponse().getHeader("X-Request-Id"))
                .isEqualTo("client-supplied-trace-1234");
    }
}
