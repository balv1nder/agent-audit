package com.auditor.agentaudit.finance;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;

import org.springframework.stereotype.Service;

import lombok.extern.slf4j.Slf4j;

@Service
@Slf4j
public class FinancialService {
    private final MockFinancialData dataSource;

    public FinancialService(MockFinancialData dataSource) {
        this.dataSource = dataSource;
    }

    public BigDecimal getVaR(String portfolio, LocalDate date) {
        log.debug("[FinancialService::getVaR]::looking up VaR for portfolio={} date={}", portfolio, date);
        return dataSource.getAllRecords()
                .stream()
                .filter(record -> record.portfolio().equals(portfolio))
                .filter(record -> record.date().equals(date))
                .map(VaRRecord::varInMillions)
                .findFirst()
                .orElseThrow(() -> {
                    IllegalArgumentException e = new IllegalArgumentException(
                            "No VaR data found for portfolio " + portfolio + " on " + date);
                    log.error("[FinancialService::getVaR]::no record for portfolio={} date={} with exception: {}",
                            portfolio, date, e.getMessage());
                    return e;
                });
    }

    public VaRChange getVaRChange(String portfolio, LocalDate previousDate, LocalDate currentDate) {
        BigDecimal previousVar = getVaR(portfolio, previousDate);

        BigDecimal currentVar = getVaR(portfolio, currentDate);

        BigDecimal absoluteChange = currentVar.subtract(previousVar);

        if (previousVar.compareTo(BigDecimal.ZERO) == 0) {
            IllegalArgumentException e = new IllegalArgumentException(
                    "Previous VaR is zero, cannot calculate percentage change");
            log.error("[FinancialService::getVaRChange]::previous VaR is zero for portfolio={} with exception: {}",
                    portfolio, e.getMessage());
            throw e;
        }

        BigDecimal percentageChange = absoluteChange
                .multiply(new BigDecimal("100"))
                .divide(previousVar, 2, RoundingMode.HALF_UP);

        log.debug("[FinancialService::getVaRChange]::portfolio={} {} -> {} change={}",
                portfolio, previousVar, currentVar, absoluteChange);

        return new VaRChange(
                portfolio,
                previousDate,
                currentDate,
                previousVar,
                currentVar,
                absoluteChange,
                percentageChange
        );
    }
}