package com.fintrack.insight.service.ai;

import com.fintrack.insight.service.SummaryGenerator;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * With AI enabled + a key, ClaudeSummaryGenerator becomes the @Primary
 * SummaryGenerator (ADR 012). Proves the conditional wiring without calling
 * Anthropic — the client is only exercised on an actual request.
 */
@SpringBootTest(properties = {
        "insight.ai.enabled=true",
        "insight.ai.api-key=sk-test-dummy"
})
class AiSummaryWiringIT {

    @Autowired
    private SummaryGenerator summaryGenerator;

    @Test
    void claudeSummaryGeneratorIsPrimaryWhenEnabled() {
        assertThat(summaryGenerator).isInstanceOf(ClaudeSummaryGenerator.class);
    }
}
