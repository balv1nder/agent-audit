package com.auditor.agentaudit.finance;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import org.springframework.stereotype.Component;

@Component
public class MockFinancialData {
    private final List<VaRRecord> records = List.of(
            new VaRRecord(
                    "Portfolio A",
                    LocalDate.of(2026, 10, 5),
                    new BigDecimal("9.0")
            ),
            new VaRRecord(
                    "Portfolio A",
                    LocalDate.of(2026, 10, 6),
                    new BigDecimal("12.0")
            ),
            new VaRRecord(
                    "Portfolio B",
                    LocalDate.of(2026, 10, 5),
                    new BigDecimal("15.0")
            ),
            new VaRRecord(
                    "Portfolio B",
                    LocalDate.of(2026, 10, 6),
                    new BigDecimal("14.0")
            )
    );

    public List<VaRRecord> getAllRecords() {
        return records;
    }
}
