package com.auditor.agentaudit.finance;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * The result of ranking two portfolios by VaR on the same date.
 *
 * <p>{@code higherPortfolio} is the conclusion, decided in Java. It exists so
 * that no consumer -- including a language model -- has to compare the two
 * figures itself.
 */
public record VaRComparison(
        LocalDate date,
        String firstPortfolio,
        BigDecimal firstVaRInMillions,
        String secondPortfolio,
        BigDecimal secondVaRInMillions,
        String higherPortfolio,
        String lowerPortfolio,
        BigDecimal differenceInMillions,
        boolean equal
) {}
