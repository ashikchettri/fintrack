package com.fintrack.auth.web;

import com.fintrack.auth.TestcontainersConfiguration;
import com.fintrack.auth.service.TokenService;
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

/** Cookie-based refresh flow (ADR 003): rotation, reuse detection, logout. */
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
class RefreshIntegrationTest {

    private static final String PASSWORD = "correct horse battery staple";
    private static final String COOKIE = RefreshTokenCookies.COOKIE_NAME;

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private com.fintrack.auth.repository.RefreshTokenRepository refreshTokenRepository;

    private String signupAndLoginCookie(String email) throws Exception {
        mockMvc.perform(post("/api/v1/auth/signup").contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email": "%s", "password": "%s"}""".formatted(email, PASSWORD)))
                .andExpect(status().isCreated());
        MvcResult result = mockMvc.perform(post("/api/v1/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email": "%s", "password": "%s"}""".formatted(email, PASSWORD)))
                .andExpect(status().isOk())
                .andReturn();
        return result.getResponse().getCookie(COOKIE).getValue();
    }

    private MvcResult refreshWithCookie(String refreshToken, int expectedStatus) throws Exception {
        return mockMvc.perform(post("/api/v1/auth/refresh").cookie(new Cookie(COOKIE, refreshToken)))
                .andExpect(status().is(expectedStatus))
                .andReturn();
    }

    @Test
    void refreshRotatesTheCookie() throws Exception {
        String firstRefresh = signupAndLoginCookie("rotate@example.com");

        MvcResult result = refreshWithCookie(firstRefresh, 200);
        String secondRefresh = result.getResponse().getCookie(COOKIE).getValue();

        assertThat(secondRefresh).isNotEqualTo(firstRefresh);
        // body carries only the access token (ADR 003)
        assertThat(result.getResponse().getContentAsString())
                .contains("accessToken").doesNotContain("refreshToken");

        var old = refreshTokenRepository
                .findByTokenHash(TokenService.sha256Hex(firstRefresh)).orElseThrow();
        var next = refreshTokenRepository
                .findByTokenHash(TokenService.sha256Hex(secondRefresh)).orElseThrow();
        assertThat(old.isRevoked()).isTrue();
        assertThat(old.getReplacedBy()).isEqualTo(next.getId());
        assertThat(next.isRevoked()).isFalse();
    }

    @Test
    void reusingARotatedCookieKillsTheWholeSessionFamily() throws Exception {
        String stolen = signupAndLoginCookie("victim-reuse@example.com");

        String successor = refreshWithCookie(stolen, 200)
                .getResponse().getCookie(COOKIE).getValue();

        refreshWithCookie(stolen, 401);      // replay of the rotated token
        refreshWithCookie(successor, 401);   // reuse detection killed the successor too
    }

    @Test
    void missingCookieIsTheSameGeneric401() throws Exception {
        mockMvc.perform(post("/api/v1/auth/refresh"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.title").value("Invalid credentials"));
    }

    @Test
    void logoutRevokesAndClearsTheCookie() throws Exception {
        String refreshToken = signupAndLoginCookie("logout@example.com");

        MvcResult result = mockMvc.perform(post("/api/v1/auth/logout")
                        .cookie(new Cookie(COOKIE, refreshToken)))
                .andExpect(status().isNoContent())
                .andReturn();

        // Max-Age=0 instructs the browser to delete the cookie
        assertThat(result.getResponse().getHeader("Set-Cookie")).contains("Max-Age=0");
        refreshWithCookie(refreshToken, 401);
    }

    @Test
    void logoutWithoutOrWithGarbageCookieIsSilent() throws Exception {
        mockMvc.perform(post("/api/v1/auth/logout"))
                .andExpect(status().isNoContent());
        mockMvc.perform(post("/api/v1/auth/logout")
                        .cookie(new Cookie(COOKIE, "completely-made-up")))
                .andExpect(status().isNoContent());
    }

    @Test
    void garbageRefreshCookieGets401ProblemDetail() throws Exception {
        refreshWithCookie("completely-made-up", 401);
    }
}
