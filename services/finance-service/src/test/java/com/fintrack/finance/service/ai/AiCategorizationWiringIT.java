package com.fintrack.finance.service.ai;

import com.fintrack.finance.TestcontainersConfiguration;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * With AI enabled + a key present, the ClaudeCategorizer becomes the @Primary
 * TransactionCategorizer (ADR 009). Proves the conditional wiring without ever
 * calling Anthropic — the client is only exercised on an actual import.
 */
@SpringBootTest(properties = {
        "finance.ai.categorization.enabled=true",
        "finance.ai.categorization.api-key=sk-test-dummy"
})
@Import(TestcontainersConfiguration.class)
class AiCategorizationWiringIT {

    @Autowired
    private TransactionCategorizer categorizer;

    @Test
    void claudeCategorizerIsPrimaryWhenEnabled() {
        assertThat(categorizer).isInstanceOf(ClaudeCategorizer.class);
    }
}
