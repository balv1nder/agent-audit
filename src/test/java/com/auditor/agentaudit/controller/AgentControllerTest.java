package com.auditor.agentaudit.controller;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import com.auditor.agentaudit.agent.Agent;
import com.auditor.agentaudit.agent.AgentAnswer;

import org.mockito.BDDMockito;

@WebMvcTest(AgentController.class)
class AgentControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private Agent agent;

    @Test
    void returnsTheAgentAnswer() throws Exception {
        BDDMockito.given(agent.answer("session-001", "Why did Portfolio A's VaR increase?"))
                .willReturn(new AgentAnswer("session-001", "Why did Portfolio A's VaR increase?", "It rose by EUR 3M."));

        mockMvc.perform(post("/api/agent/ask")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"sessionId":"session-001","question":"Why did Portfolio A's VaR increase?"}"""))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.answer").value("It rose by EUR 3M."));
    }

    @Test
    void rejectsABlankQuestion() throws Exception {
        // Handled by Spring's built-in validation support: @Valid fails, so the
        // controller method never runs and the response is a 400. We write no
        // code for this case.
        mockMvc.perform(post("/api/agent/ask")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"sessionId":"session-001","question":"  "}"""))
                .andExpect(status().isBadRequest());
    }

    @Test
    void reportsAnUnknownPortfolioAsABadRequest() throws Exception {
        // This is the case ApiExceptionHandler exists for: FinancialService
        // throws IllegalArgumentException, and without the handler Spring would
        // turn that into a misleading 500.
        BDDMockito.given(agent.answer("session-002", "VaR for Portfolio Z?"))
                .willThrow(new IllegalArgumentException("No VaR data found for portfolio Portfolio Z"));

        mockMvc.perform(post("/api/agent/ask")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"sessionId":"session-002","question":"VaR for Portfolio Z?"}"""))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("No VaR data found for portfolio Portfolio Z"));
    }
}
