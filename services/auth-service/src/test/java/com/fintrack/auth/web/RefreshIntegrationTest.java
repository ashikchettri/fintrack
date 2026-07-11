package com.fintrack.auth.web;

import com.fintrack.auth.TestcontainersConfiguration;
import com.fintrack.auth.service.TokenService;
import com.jayway.jsonpath.JsonPath;
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

@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
class RefreshIntegrationTest {

    private static final String PASSWORD = "correct horse battery staple";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private com.fintrack.auth.repository.RefreshTokenRepository refreshTokenRepository;

    private String signupAndLogin(String email) throws Exception {
        mockMvc.perform(post("/api/v1/auth/signup").contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email": "%s", "password": "%s"}""".formatted(email, PASSWORD)))
                .andExpect(status().isCreated());
        String body = mockMvc.perform(post("/api/v1/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email": "%s", "password": "%s"}""".formatted(email, PASSWORD)))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        return JsonPath.read(body, "$.refreshToken");
    }

    private String refresh(String refreshToken, int expectedStatus) throws Exception {
        var result = mockMvc.perform(post("/api/v1/auth/refresh").contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"refreshToken": "%s"}""".formatted(refreshToken)))
                .andExpect(status().is(expectedStatus))
                .andReturn();
        return result.getResponse().getContentAsString();
    }

    @Test
    void refreshRotatesTheTokenPair() throws Exception {
        String firstRefresh = signupAndLogin("rotate@example.com");

        String body = refresh(firstRefresh, 200);
        String secondRefresh = JsonPath.read(body, "$.refreshToken");

        assertThat(secondRefresh).isNotEqualTo(firstRefresh);
        assertThat((String) JsonPath.read(body, "$.accessToken")).isNotBlank();
        assertThat((String) JsonPath.read(body, "$.tokenType")).isEqualTo("Bearer");

        // the old token is revoked and chained to its successor
        var old = refreshTokenRepository
                .findByTokenHash(TokenService.sha256Hex(firstRefresh)).orElseThrow();
        var next = refreshTokenRepository
                .findByTokenHash(TokenService.sha256Hex(secondRefresh)).orElseThrow();
        assertThat(old.isRevoked()).isTrue();
        assertThat(old.getReplacedBy()).isEqualTo(next.getId());
        assertThat(next.isRevoked()).isFalse();
    }

    @Test
    void reusingARotatedTokenKillsTheWholeSessionFamily() throws Exception {
        String stolen = signupAndLogin("victim-reuse@example.com");

        // legitimate client rotates
        String successor = JsonPath.read(refresh(stolen, 200), "$.refreshToken");

        // attacker replays the old token → 401
        refresh(stolen, 401);

        // and the successor is now dead too — everyone re-authenticates
        refresh(successor, 401);
    }

    @Test
    void logoutRevokesTheRefreshToken() throws Exception {
        String refreshToken = signupAndLogin("logout@example.com");

        mockMvc.perform(post("/api/v1/auth/logout").contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"refreshToken": "%s"}""".formatted(refreshToken)))
                .andExpect(status().isNoContent());

        // revoked → refresh now fails
        refresh(refreshToken, 401);
    }

    @Test
    void logoutWithGarbageTokenIsSilent() throws Exception {
        mockMvc.perform(post("/api/v1/auth/logout").contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"refreshToken": "completely-made-up"}"""))
                .andExpect(status().isNoContent());
    }

    @Test
    void garbageRefreshTokenGets401ProblemDetail() throws Exception {
        mockMvc.perform(post("/api/v1/auth/refresh").contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"refreshToken": "completely-made-up"}"""))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.title").value("Invalid credentials"));
    }
}
