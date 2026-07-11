package com.fintrack.auth.web;

import com.fintrack.auth.TestcontainersConfiguration;
import com.fintrack.auth.domain.User;
import com.fintrack.auth.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
// Boot 4 package (moved from org.springframework.boot.test.autoconfigure.web.servlet)
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Full-stack tests: real HTTP semantics through the security filter chain,
 * real Flyway-migrated Postgres via Testcontainers. This is where DB
 * constraints, transactions, and serialization are actually exercised.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
class SignupIntegrationTest {

    private static final String SIGNUP = "/api/v1/auth/signup";
    private static final String VALID_PASSWORD = "correct horse battery staple";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private UserRepository userRepository;

    private static String body(String email, String password) {
        return """
                {"email": "%s", "password": "%s"}
                """.formatted(email, password);
    }

    @Test
    void signupReturns201WithOwnerMembershipAndNoSecrets() throws Exception {
        mockMvc.perform(post(SIGNUP)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body("alice@example.com", VALID_PASSWORD)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.userId").isNotEmpty())
                .andExpect(jsonPath("$.email").value("alice@example.com"))
                .andExpect(jsonPath("$.householdId").isNotEmpty())
                .andExpect(jsonPath("$.householdName").value("alice's household"))
                .andExpect(jsonPath("$.role").value("OWNER"))
                .andExpect(jsonPath("$.createdAt").isNotEmpty())
                // never leak credentials in any form
                .andExpect(jsonPath("$.password").doesNotExist())
                .andExpect(jsonPath("$.passwordHash").doesNotExist());
    }

    @Test
    void passwordIsStoredAsArgon2idHashNotPlaintext() throws Exception {
        mockMvc.perform(post(SIGNUP)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body("bob@example.com", VALID_PASSWORD)))
                .andExpect(status().isCreated());

        User stored = userRepository.findByEmail("bob@example.com").orElseThrow();
        assertThat(stored.getPasswordHash()).startsWith("$argon2id$");
        assertThat(stored.getPasswordHash()).doesNotContain(VALID_PASSWORD);
    }

    @Test
    void duplicateEmailReturns409ProblemDetail() throws Exception {
        mockMvc.perform(post(SIGNUP)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body("carol@example.com", VALID_PASSWORD)))
                .andExpect(status().isCreated());

        // different case — normalization must make it collide
        mockMvc.perform(post(SIGNUP)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body("Carol@Example.COM", VALID_PASSWORD)))
                .andExpect(status().isConflict())
                .andExpect(header().string("Content-Type", MediaType.APPLICATION_PROBLEM_JSON_VALUE))
                .andExpect(jsonPath("$.status").value(409))
                .andExpect(jsonPath("$.title").value("Email already in use"));
    }

    @Test
    void invalidEmailReturns400WithFieldError() throws Exception {
        mockMvc.perform(post(SIGNUP)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body("not-an-email", VALID_PASSWORD)))
                .andExpect(status().isBadRequest())
                .andExpect(header().string("Content-Type", MediaType.APPLICATION_PROBLEM_JSON_VALUE))
                .andExpect(jsonPath("$.title").value("Validation error"))
                .andExpect(jsonPath("$.errors.email").isNotEmpty());
    }

    @Test
    void shortPasswordReturns400WithFieldError() throws Exception {
        mockMvc.perform(post(SIGNUP)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body("dave@example.com", "short")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.password").isNotEmpty());

        // validation failure must not create the user
        assertThat(userRepository.existsByEmail("dave@example.com")).isFalse();
    }

    @Test
    void malformedJsonReturns400ProblemDetail() throws Exception {
        mockMvc.perform(post(SIGNUP)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{not json"))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentType(MediaType.APPLICATION_PROBLEM_JSON));
    }

    @Test
    void protectedEndpointsStillRequireAuthentication() throws Exception {
        // signup being public must not have opened the rest of the API
        mockMvc.perform(post("/api/v1/auth/anything-else"))
                .andExpect(status().isUnauthorized());
    }
}