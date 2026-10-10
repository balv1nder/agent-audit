package com.auditor.agentaudit.agent;

import java.time.LocalDate;

import org.springframework.stereotype.Service;

import com.auditor.agentaudit.finance.FinancialService;
import com.auditor.agentaudit.finance.VaRChange;

import lombok.extern.slf4j.Slf4j;

/**
 * A placeholder "agent" with no intelligence at all: it always runs the same
 * VaR-change calculation and renders the result as a sentence.
 *
 * <p>Its job in Phase 1 is to prove the wiring end to end -- HTTP in, real
 * calculation over the mock dataset, text out -- before an LLM is introduced.
 * Everything it reports is computed by {@link FinancialService}; no number here
 * is invented, which is the property we want to preserve once the LLM arrives.
 */
@Service
@Slf4j
public class DeterministicAgent implements Agent {

    private static final String DEFAULT_PORTFOLIO = "Portfolio A";
    private static final LocalDate PREVIOUS_DATE = LocalDate.of(2026, 10, 5);
    private static final LocalDate CURRENT_DATE = LocalDate.of(2026, 10, 6);

    private final FinancialService financialService;

    public DeterministicAgent(FinancialService financialService) {
        this.financialService = financialService;
    }

    @Override
    public AgentAnswer answer(String sessionId, String question) {
        log.info("[DeterministicAgent::answer]::handling sessionId={} question={}", sessionId, question);

        VaRChange change = financialService.getVaRChange(
                DEFAULT_PORTFOLIO, PREVIOUS_DATE, CURRENT_DATE);

        String text = "%s VaR moved from EUR %sM on %s to EUR %sM on %s: a change of EUR %sM (%s%%)."
                .formatted(
                        change.portfolio(),
                        change.previousVaRInMillions(),
                        change.previousDate(),
                        change.currentVaRInMillions(),
                        change.currentDate(),
                        change.absoluteChangeInMillions(),
                        change.percentageChange());

        log.info("[DeterministicAgent::answer]::answered sessionId={}", sessionId);

        return new AgentAnswer(sessionId, question, text);
    }
}
