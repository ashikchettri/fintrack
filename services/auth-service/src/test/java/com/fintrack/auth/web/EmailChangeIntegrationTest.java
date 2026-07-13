package com.fintrack.auth.web;

import com.fintrack.auth.TestcontainersConfiguration;
import com.fintrack.auth.testsupport.RecordingEmailSender;
import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.http.HttpHeaders.AUTHORIZATION;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
class EmailChangeIntegrationTest {

    private static final String PASSWORD = "correct horse battery staple";

    @Autowired
    private MockMvc mockMvc;

    private String signupVerifyLogin(String email) throws Exception {
        mockMvc.perform(post("/api/v1/auth/signup").contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email": "%s", "password": "%s"}""".formatted(email, PASSWORD)))
                .andExpect(status().isCreated());
        mockMvc.perform(post("/api/v1/auth/verify-email").contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email": "%s", "code": "%s"}"""
                                .formatted(email, RecordingEmailSender.lastCodeFor(email))))
                .andExpect(status().isNoContent());
        var result = mockMvc.perform(post("/api/v1/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email": "%s", "password": "%s"}""".formatted(email, PASSWORD)))
                .andExpect(status().isOk())
                .andReturn();
        return JsonPath.read(result.getResponse().getContentAsString(), "$.accessToken");
    }

    private void requestChange(String token, String newEmail, String password, int expected) throws Exception {
        mockMvc.perform(post("/api/v1/users/me/email")
                        .header(AUTHORIZATION, "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"newEmail": "%s", "currentPassword": "%s"}""".formatted(newEmail, password)))
                .andExpect(status().is(expected));
    }

    private void confirm(String token, String code, int expected) throws Exception {
        mockMvc.perform(post("/api/v1/users/me/email/verify")
                        .header(AUTHORIZATION, "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"code": "%s"}""".formatted(code)))
                .andExpect(status().is(expected));
    }

    @Test
    void fullChangeFlowSwapsTheLoginEmail() throws Exception {
        String token = signupVerifyLogin("before@example.com");

        requestChange(token, "after@example.com", PASSWORD, 204);
        // old email still logs in until confirmation
        mockMvc.perform(post("/api/v1/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email": "before@example.com", "password": "%s"}""".formatted(PASSWORD)))
                .andExpect(status().isOk());

        confirm(token, RecordingEmailSender.lastChangeCodeFor("after@example.com"), 204);

        // profile and login now use the new address; old address is gone
        mockMvc.perform(get("/api/v1/users/me").header(AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.email").value("after@example.com"));
        mockMvc.perform(post("/api/v1/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email": "after@example.com", "password": "%s"}""".formatted(PASSWORD)))
                .andExpect(status().isOk());
        mockMvc.perform(post("/api/v1/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email": "before@example.com", "password": "%s"}""".formatted(PASSWORD)))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void wrongPasswordIs400() throws Exception {
        String token = signupVerifyLogin("badpw-change@example.com");
        requestChange(token, "whatever@example.com", "not-my-password", 400);
    }

    @Test
    void newEmailAlreadyInUseIs409() throws Exception {
        signupVerifyLogin("taken-target@example.com");
        String token = signupVerifyLogin("wants-taken@example.com");

        requestChange(token, "taken-target@example.com", PASSWORD, 409);
    }

    @Test
    void wrongConfirmationCodeIs400() throws Exception {
        String token = signupVerifyLogin("badcode-change@example.com");
        requestChange(token, "newcode@example.com", PASSWORD, 204);
        String code = RecordingEmailSender.lastChangeCodeFor("newcode@example.com");

        confirm(token, code.equals("000000") ? "111111" : "000000", 400);
    }

    @Test
    void emailChangeRequiresAuthentication() throws Exception {
        mockMvc.perform(post("/api/v1/users/me/email").contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"newEmail": "x@example.com", "currentPassword": "y"}"""))
                .andExpect(status().isUnauthorized());
    }
}
