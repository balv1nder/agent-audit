package com.auditor.agentaudit.tools;

import static org.assertj.core.api.Assertions.assertThat;

import java.lang.reflect.Method;
import java.util.Arrays;

import org.junit.jupiter.api.Test;
import org.springframework.ai.tool.annotation.Tool;

/**
 * Guards the annotations that drive tool behaviour.
 *
 * <p>These are not ordinary configuration. A tool method has no call site --
 * Spring AI resolves it by name and invokes it reflectively -- so an annotation
 * silently lost or attached to the wrong method changes behaviour with nothing
 * to review and no compiler error. Both have happened in this codebase.
 */
class FinancialToolsAnnotationTest {

    private Tool toolAnnotation(String methodName) {
        Method method = Arrays.stream(FinancialTools.class.getDeclaredMethods())
                .filter(m -> m.getName().equals(methodName))
                .findFirst()
                .orElseThrow(() -> new AssertionError("no method named " + methodName));
        Tool tool = method.getAnnotation(Tool.class);
        assertThat(tool).as("%s must be exposed to the model", methodName).isNotNull();
        return tool;
    }

    @Test
    void comparisonBypassesTheModelAndReturnsPlainText() {
        Tool tool = toolAnnotation("compareVaR");

        // The model demonstrably got this comparison wrong, so its result goes
        // straight back to the caller...
        assertThat(tool.returnDirect()).isTrue();
        // ...which means it must not arrive JSON-quoted.
        assertThat(tool.resultConverter()).isEqualTo(PlainTextResultConverter.class);
    }

    @Test
    void everyOtherToolLetsTheModelPhraseTheAnswer() {
        for (String name : new String[] { "getVaR", "getVaRChange", "listAvailableData" }) {
            assertThat(toolAnnotation(name).returnDirect())
                    .as("%s should not short-circuit the agent loop", name)
                    .isFalse();
        }
    }
}
