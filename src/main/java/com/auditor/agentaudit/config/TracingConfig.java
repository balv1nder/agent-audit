package com.auditor.agentaudit.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import io.opentelemetry.exporter.logging.LoggingSpanExporter;
import io.opentelemetry.sdk.trace.export.SpanExporter;

/**
 * Sends finished spans to the console.
 *
 * <p>A deployment would export to a collector -- Jaeger, Tempo, any OTLP
 * endpoint -- and view traces there. Printing them keeps this project free of
 * extra infrastructure while showing exactly what would be sent, which is the
 * part that matters for the argument: the same execution produces telemetry
 * for operators and a separate, durable record for reviewers.
 */
@Configuration
public class TracingConfig {

    @Bean
    public SpanExporter loggingSpanExporter() {
        return LoggingSpanExporter.create();
    }
}
