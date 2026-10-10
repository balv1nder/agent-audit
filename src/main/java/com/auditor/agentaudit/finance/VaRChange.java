package com.auditor.agentaudit.finance;

import java.math.BigDecimal;
import java.time.LocalDate;

public record VaRChange (
    String portfolio,
    LocalDate previousDate,
    LocalDate currentDate,
    BigDecimal previousVaRInMillions,
    BigDecimal currentVaRInMillions,
    BigDecimal absoluteChangeInMillions,
    BigDecimal percentageChange
) {}
