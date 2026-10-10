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

    /**
     * Ranks two portfolios by VaR on the same date.
     *
     * <p>The comparison itself is done here, in deterministic Java, and the
     * winner is returned as an explicit field. A caller never has to work out
     * which figure is larger.
     */
    public VaRComparison compareVaR(String firstPortfolio, String secondPortfolio, LocalDate date) {
        BigDecimal firstVar = getVaR(firstPortfolio, date);
        BigDecimal secondVar = getVaR(secondPortfolio, date);

        int comparison = firstVar.compareTo(secondVar);
        boolean equal = comparison == 0;

        String higher = equal ? null : (comparison > 0 ? firstPortfolio : secondPortfolio);
        String lower = equal ? null : (comparison > 0 ? secondPortfolio : firstPortfolio);
        BigDecimal difference = firstVar.subtract(secondVar).abs();

        log.debug("[FinancialService::compareVaR]::date={} {}={} {}={} higher={}",
                date, firstPortfolio, firstVar, secondPortfolio, secondVar, higher);

        return new VaRComparison(
                date,
                firstPortfolio, firstVar,
                secondPortfolio, secondVar,
                higher, lower, difference, equal);
    }
}
