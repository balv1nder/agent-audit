package com.auditor.agentaudit.finance;

import java.math.BigDecimal;
import java.time.LocalDate;

public record VaRRecord(
        String portfolio,
        LocalDate date,
        BigDecimal varInMillions
) {
}