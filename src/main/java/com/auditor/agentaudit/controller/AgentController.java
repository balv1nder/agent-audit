package com.auditor.agentaudit.controller;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.auditor.agentaudit.agent.Agent;
import com.auditor.agentaudit.agent.AgentAnswer;

import jakarta.validation.Valid;

import lombok.extern.slf4j.Slf4j;

/** HTTP entry point for asking the agent a question. */
@RestController
@RequestMapping("/api/agent")
@Slf4j
public class AgentController {

    private final Agent agent;

    public AgentController(Agent agent) {
        this.agent = agent;
    }

    @PostMapping("/ask")
    public ResponseEntity<AgentAnswer> ask(@Valid @RequestBody AskRequest request) {
        log.info("[AgentController::ask]::received sessionId={}", request.sessionId());

        AgentAnswer answer = agent.answer(request.sessionId(), request.question());

        log.info("[AgentController::ask]::responding sessionId={}", request.sessionId());
        return ResponseEntity.ok(answer);
    }
}
