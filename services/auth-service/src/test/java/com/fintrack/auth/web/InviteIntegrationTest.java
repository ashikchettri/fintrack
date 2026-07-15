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
import org.springframework.test.web.servlet.MvcResult;

import static org.springframework.http.HttpHeaders.AUTHORIZATION;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * The multi-member flow end-to-end: an OWNER invites someone, they accept into
 * the SAME household as an ADULT, and the roster shows both with names — the
 * thing that makes shared commitments (ADR 006) real.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
class InviteIntegrationTest {

    private static final String PASSWORD = "correct horse battery staple";

    @Autowired
    private MockMvc mockMvc;

    private record Owner(String token, String householdId) {
    }

    private Owner signupVerifyLogin(String email) throws Exception {
        String householdId = JsonPath.read(
                mockMvc.perform(post("/api/v1/auth/signup").contentType(MediaType.APPLICATION_JSON)
                                .content("""
                                        {"email": "%s", "password": "%s"}""".formatted(email, PASSWORD)))
                        .andExpect(status().isCreated())
                        .andReturn().getResponse().getContentAsString(),
                "$.householdId");
        mockMvc.perform(post("/api/v1/auth/verify-email").contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email": "%s", "code": "%s"}"""
                                .formatted(email, RecordingEmailSender.lastCodeFor(email))))
                .andExpect(status().isNoContent());
        MvcResult login = mockMvc.perform(post("/api/v1/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email": "%s", "password": "%s"}""".formatted(email, PASSWORD)))
                .andExpect(status().isOk()).andReturn();
        return new Owner(JsonPath.read(login.getResponse().getContentAsString(), "$.accessToken"), householdId);
    }

    private void invite(String token, String email) throws Exception {
        mockMvc.perform(post("/api/v1/households/invites")
                        .header(AUTHORIZATION, "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email": "%s"}""".formatted(email)))
                .andExpect(status().isAccepted());
    }

    @Test
    void ownerInvitesAndInviteeJoinsTheSameHousehold() throws Exception {
        Owner owner = signupVerifyLogin("owner-a@example.com");
        String partner = "partner-a@example.com";

        invite(owner.token(), partner);
        String code = RecordingEmailSender.lastInviteCodeFor(partner);

        // accept → new ADULT member in the SAME household, no login needed to accept
        mockMvc.perform(post("/api/v1/households/invites/accept").contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email": "%s", "code": "%s", "password": "%s", "name": "Ashik"}"""
                                .formatted(partner, code, PASSWORD)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.role").value("ADULT"))
                .andExpect(jsonPath("$.householdId").value(owner.householdId()))
                .andExpect(jsonPath("$.email").value(partner));

        // the partner can now log in
        mockMvc.perform(post("/api/v1/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email": "%s", "password": "%s"}""".formatted(partner, PASSWORD)))
                .andExpect(status().isOk());

        // the roster shows both, with names + the "you" flag from the owner's view
        mockMvc.perform(get("/api/v1/households/members").header(AUTHORIZATION, "Bearer " + owner.token()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[?(@.role=='OWNER')].name").value("owner-a"))       // email fallback
                .andExpect(jsonPath("$[?(@.role=='OWNER')].isYou").value(true))
                .andExpect(jsonPath("$[?(@.role=='ADULT')].name").value("Ashik"))          // chosen name
                .andExpect(jsonPath("$[?(@.role=='ADULT')].isYou").value(false));
    }

    @Test
    void onlyTheOwnerCanInvite() throws Exception {
        Owner owner = signupVerifyLogin("owner-b@example.com");
        String partner = "partner-b@example.com";
        invite(owner.token(), partner);
        mockMvc.perform(post("/api/v1/households/invites/accept").contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email": "%s", "code": "%s", "password": "%s", "name": "Sam"}"""
                                .formatted(partner, RecordingEmailSender.lastInviteCodeFor(partner), PASSWORD)))
                .andExpect(status().isCreated());

        // the new ADULT logs in and tries to invite → 403
        MvcResult login = mockMvc.perform(post("/api/v1/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email": "%s", "password": "%s"}""".formatted(partner, PASSWORD)))
                .andExpect(status().isOk()).andReturn();
        String partnerToken = JsonPath.read(login.getResponse().getContentAsString(), "$.accessToken");

        mockMvc.perform(post("/api/v1/households/invites")
                        .header(AUTHORIZATION, "Bearer " + partnerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email": "someone@example.com"}"""))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.title").value("Not the household owner"));
    }

    @Test
    void aWrongCodeIsRejected() throws Exception {
        Owner owner = signupVerifyLogin("owner-c@example.com");
        String partner = "partner-c@example.com";
        invite(owner.token(), partner);

        mockMvc.perform(post("/api/v1/households/invites/accept").contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email": "%s", "code": "000000", "password": "%s", "name": "X"}"""
                                .formatted(partner, PASSWORD)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.title").value("Invalid invitation"));
    }

    @Test
    void invitingAnExistingEmailIs409() throws Exception {
        Owner owner = signupVerifyLogin("owner-d@example.com");
        // owner-d already has an account → can't be invited (fresh-account accept only)
        mockMvc.perform(post("/api/v1/households/invites")
                        .header(AUTHORIZATION, "Bearer " + owner.token())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email": "owner-d@example.com"}"""))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.title").value("Email already in use"));
    }

    @Test
    void listingMembersRequiresAuthentication() throws Exception {
        mockMvc.perform(get("/api/v1/households/members")).andExpect(status().isUnauthorized());
    }
}
