package com.fintrack.auth.web;

import com.fintrack.auth.TestcontainersConfiguration;
import com.fintrack.auth.service.TokenService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.time.Duration;

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
class LoginIntegrationTest {

    private static final String SIGNUP = "/api/v1/auth/signup";
    private static final String LOGIN = "/api/v1/auth/login";
    private static final String PASSWORD = "correct horse battery staple";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JwtDecoder jwtDecoder;

    @Autowired
    private com.fintrack.auth.repository.RefreshTokenRepository refreshTokenRepository;

    private static String body(String email, String password) {
        return """
                {"email": "%s", "password": "%s"}
                """.formatted(email, password);
    }

    private void signup(String email) throws Exception {
        mockMvc.perform(post(SIGNUP).contentType(MediaType.APPLICATION_JSON)
                        .content(body(email, PASSWORD)))
                .andExpect(status().isCreated());
        // login requires a verified mailbox (ADR 004) — code via the recording sender
        mockMvc.perform(post("/api/v1/auth/verify-email").contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email": "%s", "code": "%s"}"""
                                .formatted(email, com.fintrack.auth.testsupport.RecordingEmailSender.lastCodeFor(email))))
                .andExpect(status().isNoContent());
    }

    private String loginAndExtract(String email, String jsonPath) throws Exception {
        MvcResult result = mockMvc.perform(post(LOGIN).contentType(MediaType.APPLICATION_JSON)
                        .content(body(email, PASSWORD)))
                .andExpect(status().isOk())
                .andReturn();
        return com.jayway.jsonpath.JsonPath.read(result.getResponse().getContentAsString(), jsonPath);
    }

    @Test
    void loginReturnsBearerTokenAndRefreshCookie() throws Exception {
        signup("login-ok@example.com");

        MvcResult result = mockMvc.perform(post(LOGIN).contentType(MediaType.APPLICATION_JSON)
                        .content(body("Login-OK@example.com", PASSWORD)))  // case-insensitive
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accessToken").isNotEmpty())
                // ADR 003: refresh token is cookie-only, never in the body
                .andExpect(jsonPath("$.refreshToken").doesNotExist())
                .andExpect(jsonPath("$.tokenType").value("Bearer"))
                .andExpect(jsonPath("$.expiresInSeconds").value(900))
                .andReturn();

        String setCookie = result.getResponse().getHeader("Set-Cookie");
        assertThat(setCookie)
                .contains(RefreshTokenCookies.COOKIE_NAME + "=")
                .contains("HttpOnly")
                .contains("SameSite=Strict")
                .contains("Path=/api/v1/auth");
    }

    @Test
    void accessTokenCarriesHouseholdClaims() throws Exception {
        signup("claims@example.com");
        String accessToken = loginAndExtract("claims@example.com", "$.accessToken");

        Jwt jwt = jwtDecoder.decode(accessToken);
        assertThat(jwt.getClaimAsString("householdId")).isNotBlank();
        assertThat(jwt.getClaimAsString("memberId")).isNotBlank();
        assertThat(jwt.getClaimAsString("role")).isEqualTo("OWNER");
        assertThat(jwt.getClaimAsString("iss")).isEqualTo("fintrack-auth");
        assertThat(Duration.between(jwt.getIssuedAt(), jwt.getExpiresAt()))
                .isEqualTo(Duration.ofMinutes(15));
    }

    @Test
    void refreshTokenIsPersistedHashedNotRaw() throws Exception {
        signup("hashed@example.com");
        MvcResult result = mockMvc.perform(post(LOGIN).contentType(MediaType.APPLICATION_JSON)
                        .content(body("hashed@example.com", PASSWORD)))
                .andExpect(status().isOk())
                .andReturn();
        String rawRefresh = result.getResponse()
                .getCookie(RefreshTokenCookies.COOKIE_NAME).getValue();

        assertThat(refreshTokenRepository.findByTokenHash(rawRefresh)).isEmpty();
        assertThat(refreshTokenRepository.findByTokenHash(TokenService.sha256Hex(rawRefresh)))
                .isPresent();
    }

    @Test
    void wrongPasswordAndUnknownEmailAreIndistinguishable() throws Exception {
        signup("victim@example.com");

        MvcResult wrongPassword = mockMvc.perform(post(LOGIN).contentType(MediaType.APPLICATION_JSON)
                        .content(body("victim@example.com", "not-the-password")))
                .andExpect(status().isUnauthorized())
                .andExpect(header().string("Content-Type", MediaType.APPLICATION_PROBLEM_JSON_VALUE))
                .andReturn();

        MvcResult unknownEmail = mockMvc.perform(post(LOGIN).contentType(MediaType.APPLICATION_JSON)
                        .content(body("ghost@example.com", "not-the-password")))
                .andExpect(status().isUnauthorized())
                .andReturn();

        // identical bodies — the response must not reveal which factor failed
        assertThat(unknownEmail.getResponse().getContentAsString())
                .isEqualTo(wrongPassword.getResponse().getContentAsString());
    }

    @Test
    void bearerTokenAuthenticatesAgainstProtectedRoutes() throws Exception {
        signup("bearer@example.com");
        String accessToken = loginAndExtract("bearer@example.com", "$.accessToken");

        // no token → 401 from the resource server
        mockMvc.perform(get("/api/v1/protected-probe"))
                .andExpect(status().isUnauthorized());

        // valid token → past auth, 404 because no such handler exists (yet)
        mockMvc.perform(get("/api/v1/protected-probe")
                        .header(AUTHORIZATION, "Bearer " + accessToken))
                .andExpect(status().isNotFound());
    }

    @Test
    void jwksEndpointServesPublicKeyOnlyAndUnauthenticated() throws Exception {
        mockMvc.perform(get("/.well-known/jwks.json"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.keys[0].kty").value("RSA"))
                .andExpect(jsonPath("$.keys[0].kid").isNotEmpty())
                .andExpect(jsonPath("$.keys[0].n").isNotEmpty())
                // private parameters must never appear
                .andExpect(jsonPath("$.keys[0].d").doesNotExist())
                .andExpect(jsonPath("$.keys[0].p").doesNotExist())
                .andExpect(jsonPath("$.keys[0].q").doesNotExist());
    }
}
