package com.auditor.agentaudit.tools;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;

import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.stereotype.Component;

import io.micrometer.tracing.Span;
import io.micrometer.tracing.Tracer;

import com.auditor.agentaudit.audit.AuditEventType;
import com.auditor.agentaudit.audit.AuditService;
import com.auditor.agentaudit.finance.FinancialService;
import com.auditor.agentaudit.finance.MockFinancialData;
import com.auditor.agentaudit.finance.VaRChange;
import com.auditor.agentaudit.finance.VaRComparison;
import com.auditor.agentaudit.finance.VaRRecord;

import lombok.extern.slf4j.Slf4j;

/**
 * The bridge between the model and the financial domain.
 *
 * <p>Each {@code @Tool} method is advertised to the LLM, which may ask for it to
 * be called. Spring AI then executes the Java method and feeds the result back
 * into the conversation. The descriptions are not documentation -- they are the
 * only thing the model reads when deciding what to call, so they are written
 * for the model, not for us.
 *
 * <p>This class deliberately holds no financial logic. Every figure comes from
 * {@link FinancialService}; the model never computes anything itself.
 *
 * <p>Dates cross this boundary as ISO strings ({@code 2026-10-06}) because a
 * model emits text, not {@code LocalDate}. Parsing and validating that text is
 * this layer's job.
 */
@Component
@Slf4j
public class FinancialTools {

    private final FinancialService financialService;
    private final MockFinancialData dataSource;
    private final AuditService auditService;
    private final Tracer tracer;

    public FinancialTools(FinancialService financialService, MockFinancialData dataSource,
                          AuditService auditService, Tracer tracer) {
        this.financialService = financialService;
        this.dataSource = dataSource;
        this.auditService = auditService;
        this.tracer = tracer;
    }

    /**
     * Records a tool invocation and its outcome around the actual work.
     *
     * <p>Every tool goes through here, so the arguments the model supplied and
     * the value returned are preserved for each call -- including when the call
     * throws, which is the case a reviewer most needs to see.
     *
     * <p>A Spring AOP aspect would remove the explicit wrapping, but tools are
     * resolved by reflection over this bean and annotation visibility through a
     * proxy is not guaranteed, so the call is made plainly instead.
     */
    private String audited(String toolName, Map<String, ?> arguments, Supplier<String> work) {
        // One span per tool call, so the trace shows the same steps as the
        // audit record and the two can be read side by side.
        Span span = tracer.nextSpan().name("tool." + toolName).start();

        try (Tracer.SpanInScope scope = tracer.withSpan(span)) {
            auditService.record(AuditEventType.TOOL_CALL, toolName, arguments);

            String result = work.get();

            auditService.record(AuditEventType.TOOL_RESULT, toolName, Map.of("result", result));
            return result;

        } catch (RuntimeException e) {
            span.error(e);
            auditService.record(AuditEventType.TOOL_ERROR, toolName, Map.of(
                    "exception", e.getClass().getSimpleName(),
                    "message", String.valueOf(e.getMessage())));
            throw e;

        } finally {
            span.end();
        }
    }

    @Tool(description = """
            Returns the Value at Risk (VaR) in millions of euros for one portfolio
            on one date. Use this when the user asks for the VaR on a single date.
            The date must be in ISO format, for example 2026-10-06.
            """)
    public String getVaR(
            @ToolParam(description = "Portfolio name, for example 'Portfolio A'") String portfolio,
            @ToolParam(description = "Date in ISO format, for example 2026-10-06") String date) {

        log.info("[FinancialTools::getVaR]::model requested tool call portfolio={} date={}", portfolio, date);

        return audited("getVaR", Map.of("portfolio", portfolio, "date", date), () -> {
            BigDecimal var = financialService.getVaR(portfolio, parseDate(date, "date"));
            String result = "VaR for %s on %s is EUR %sM".formatted(portfolio, date, var);
            log.info("[FinancialTools::getVaR]::returning to model result={}", result);
            return result;
        });
    }

    @Tool(description = """
            Compares the Value at Risk (VaR) of one portfolio between two dates and
            returns both figures, the absolute change and the percentage change.
            Use this when the user asks how VaR changed, increased or decreased.
            Prefer this over calling getVaR twice. Dates must be ISO format.
            """)
    public String getVaRChange(
            @ToolParam(description = "Portfolio name, for example 'Portfolio A'") String portfolio,
            @ToolParam(description = "The earlier date, ISO format") String previousDate,
            @ToolParam(description = "The later date, ISO format") String currentDate) {

        log.info("[FinancialTools::getVaRChange]::model requested tool call portfolio={} from={} to={}",
                portfolio, previousDate, currentDate);

        return audited("getVaRChange",
                Map.of("portfolio", portfolio, "previousDate", previousDate, "currentDate", currentDate),
                () -> {
        VaRChange change = financialService.getVaRChange(
                portfolio,
                parseDate(previousDate, "previousDate"),
                parseDate(currentDate, "currentDate"));

        String result = ("VaR for %s moved from EUR %sM on %s to EUR %sM on %s. "
                + "Absolute change EUR %sM, percentage change %s%%.").formatted(
                        change.portfolio(),
                        change.previousVaRInMillions(), change.previousDate(),
                        change.currentVaRInMillions(), change.currentDate(),
                        change.absoluteChangeInMillions(), change.percentageChange());

        log.info("[FinancialTools::getVaRChange]::returning to model result={}", result);
        return result;
        });
    }

    /**
     * {@code returnDirect = true}: the tool result is returned to the caller as
     * the final answer, without a second trip through the model.
     *
     * <p>This is deliberate. The model demonstrably got this comparison wrong --
     * it stated EUR 12M was higher than EUR 14M, reproducibly -- and then, once
     * the comparison was moved into Java, discarded the correct result instead
     * of reporting it. A ranking of two risk figures has exactly one right
     * answer and no need for paraphrase, so the model is taken out of the loop.
     */
    @Tool(returnDirect = true, resultConverter = PlainTextResultConverter.class, description = """
            Compares the Value at Risk (VaR) of two portfolios on the same date and
            states which one is higher. Use this whenever the user asks which
            portfolio is riskier, higher, lower, larger or smaller, or asks you to
            compare or rank two portfolios. Do not compare the figures yourself:
            call this tool and report the answer it gives. The date must be ISO
            format, for example 2026-10-06.
            """)
    public String compareVaR(
            @ToolParam(description = "First portfolio name, for example 'Portfolio A'") String firstPortfolio,
            @ToolParam(description = "Second portfolio name, for example 'Portfolio B'") String secondPortfolio,
            @ToolParam(description = "Date in ISO format, for example 2026-10-06") String date) {

        log.info("[FinancialTools::compareVaR]::model requested tool call first={} second={} date={}",
                firstPortfolio, secondPortfolio, date);

        return audited("compareVaR",
                Map.of("firstPortfolio", firstPortfolio, "secondPortfolio", secondPortfolio, "date", date),
                () -> {
        VaRComparison comparison = financialService.compareVaR(
                firstPortfolio, secondPortfolio, parseDate(date, "date"));

        String result;
        if (comparison.equal()) {
            result = "On %s, %s and %s have an equal VaR of EUR %sM. Neither is higher.".formatted(
                    comparison.date(), comparison.firstPortfolio(), comparison.secondPortfolio(),
                    comparison.firstVaRInMillions());
        } else {
            result = ("On %s, %s has the HIGHER VaR. %s = EUR %sM, %s = EUR %sM. "
                    + "%s is higher than %s by EUR %sM.").formatted(
                            comparison.date(), comparison.higherPortfolio(),
                            comparison.firstPortfolio(), comparison.firstVaRInMillions(),
                            comparison.secondPortfolio(), comparison.secondVaRInMillions(),
                            comparison.higherPortfolio(), comparison.lowerPortfolio(),
                            comparison.differenceInMillions());
        }

        log.info("[FinancialTools::compareVaR]::returning to model result={}", result);
        return result;
        });
    }

    @Tool(description = """
            Lists every portfolio and date for which VaR data exists. Call this
            first if you are unsure which portfolios or dates are available, or if
            a previous tool call failed because the data was not found.
            """)
    public String listAvailableData() {
        log.info("[FinancialTools::listAvailableData]::model requested tool call");

        return audited("listAvailableData", Map.of(), () -> {
        List<VaRRecord> records = dataSource.getAllRecords();
        StringBuilder sb = new StringBuilder("Available VaR data:");
        for (VaRRecord record : records) {
            sb.append("\n- %s on %s: EUR %sM".formatted(
                    record.portfolio(), record.date(), record.varInMillions()));
        }

        String result = sb.toString();
        log.info("[FinancialTools::listAvailableData]::returning {} records to model", records.size());
        return result;
        });
    }

    /**
     * The model sends text, so it can send nonsense. Failing with a clear message
     * is better than a stack trace: Spring AI hands the message back to the model,
     * which can then correct itself and retry.
     */
    private LocalDate parseDate(String value, String field) {
        try {
            return LocalDate.parse(value);
        } catch (DateTimeParseException e) {
            log.error("[FinancialTools::parseDate]::model sent an unparseable {}={} with exception: {}",
                    field, value, e.getMessage());
            throw new IllegalArgumentException(
                    "The " + field + " '" + value + "' is not a valid ISO date. Use the format 2026-10-06.");
        }
    }
}
