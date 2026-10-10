package com.auditor.agentaudit.finance;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import java.time.LocalDate;

import org.junit.jupiter.api.Test;

class FinancialServiceTest {

    private static final LocalDate MONDAY = LocalDate.of(2026, 10, 5);
    private static final LocalDate TUESDAY = LocalDate.of(2026, 10, 6);

    private final FinancialService service = new FinancialService(new MockFinancialData());

    @Test
    void returnsVaRForAKnownPortfolioAndDate() {
        assertThat(service.getVaR("Portfolio A", TUESDAY)).isEqualByComparingTo("12.0");
    }

    @Test
    void computesTheChangeBetweenTwoDates() {
        VaRChange change = service.getVaRChange("Portfolio A", MONDAY, TUESDAY);

        assertThat(change.absoluteChangeInMillions()).isEqualByComparingTo("3.0");
        assertThat(change.percentageChange()).isEqualByComparingTo(new BigDecimal("33.33"));
    }

    @Test
    void rejectsAnUnknownPortfolio() {
        assertThatThrownBy(() -> service.getVaR("Portfolio Z", TUESDAY))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Portfolio Z");
    }

    @Test
    void decidesWhichPortfolioHasTheHigherVaR() {
        // Portfolio A = 12.0, Portfolio B = 14.0 on this date.
        VaRComparison comparison = service.compareVaR("Portfolio A", "Portfolio B", TUESDAY);

        assertThat(comparison.higherPortfolio()).isEqualTo("Portfolio B");
        assertThat(comparison.lowerPortfolio()).isEqualTo("Portfolio A");
        assertThat(comparison.differenceInMillions()).isEqualByComparingTo("2.0");
        assertThat(comparison.equal()).isFalse();
    }

    @Test
    void decidesTheSameWayRegardlessOfArgumentOrder() {
        VaRComparison forward = service.compareVaR("Portfolio A", "Portfolio B", TUESDAY);
        VaRComparison reversed = service.compareVaR("Portfolio B", "Portfolio A", TUESDAY);

        assertThat(forward.higherPortfolio()).isEqualTo(reversed.higherPortfolio());
        assertThat(forward.differenceInMillions()).isEqualByComparingTo(reversed.differenceInMillions());
    }
}
