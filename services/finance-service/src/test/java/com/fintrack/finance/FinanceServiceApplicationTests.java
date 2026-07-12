package com.fintrack.finance;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;

@SpringBootTest
@Import(TestcontainersConfiguration.class)
class FinanceServiceApplicationTests {

    @Test
    void contextLoads() {
        // Boots the full context against a real throwaway Postgres. The JWKS
        // decoder is lazy — it only fetches keys on first token verification,
        // so no auth-service is needed for the context itself.
    }
}
