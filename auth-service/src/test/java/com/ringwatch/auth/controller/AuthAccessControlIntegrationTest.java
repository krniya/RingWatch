package com.ringwatch.auth.controller;

import static org.hamcrest.Matchers.notNullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ringwatch.auth.controller.dto.CreateAccountRequest;
import com.ringwatch.auth.controller.dto.LoginRequest;
import com.ringwatch.auth.model.Role;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest
@AutoConfigureMockMvc
class AuthAccessControlIntegrationTest {

    private static final String ADMIN_USERNAME = "admin";
    private static final String ADMIN_PASSWORD = "admin-test-password";

    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper objectMapper;

    @Test
    void unauthenticatedRequestToCreateAccountIsRejected() throws Exception {
        mockMvc.perform(post("/auth/accounts")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                new CreateAccountRequest("someone", "password123", Role.ANALYST))))
                .andExpect(status().isForbidden());
    }

    @Test
    void analystCannotCreateAccountsButAdminCan() throws Exception {
        String adminToken = login(ADMIN_USERNAME, ADMIN_PASSWORD);

        String analystUsername = "analyst-" + System.nanoTime();
        mockMvc.perform(post("/auth/accounts")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                new CreateAccountRequest(analystUsername, "password123", Role.ANALYST))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.role").value("ANALYST"));

        String analystToken = login(analystUsername, "password123");

        mockMvc.perform(post("/auth/accounts")
                        .header("Authorization", "Bearer " + analystToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                new CreateAccountRequest("blocked-" + System.nanoTime(), "password123", Role.ANALYST))))
                .andExpect(status().isForbidden());

        mockMvc.perform(get("/auth/me").header("Authorization", "Bearer " + analystToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.username").value(analystUsername))
                .andExpect(jsonPath("$.id").value(notNullValue()));
    }

    private String login(String username, String password) throws Exception {
        String body = mockMvc.perform(post("/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new LoginRequest(username, password))))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();
        return objectMapper.readTree(body).get("token").asText();
    }
}
