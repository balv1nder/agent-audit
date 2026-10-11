package com.auditor.agentaudit.tools;

import java.lang.reflect.Type;

import org.springframework.ai.tool.execution.ToolCallResultConverter;

/**
 * Returns a tool's result as plain text instead of JSON.
 *
 * <p>Spring AI's default converter serialises a result to JSON, which turns a
 * String return value into a quoted String -- {@code "VaR is EUR 12M"} rather
 * than {@code VaR is EUR 12M}. That is harmless when the model reads the result
 * and rewrites it, but {@code returnDirect} tools hand their output straight to
 * the caller, so the quotes end up in the response.
 */
public class PlainTextResultConverter implements ToolCallResultConverter {

    @Override
    public String convert(Object result, Type returnType) {
        return result == null ? "" : result.toString();
    }
}
