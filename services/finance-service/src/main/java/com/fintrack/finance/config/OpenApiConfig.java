package com.fintrack.finance.config;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Swagger UI for finance-service. The bearer scheme makes the Authorize button
 * accept an access token issued by auth-service (finance-service only verifies
 * it via JWKS — it never issues one). Paste a token from auth-service's /login.
 */
@Configuration
public class OpenApiConfig {

    @Bean
    OpenAPI financeServiceOpenApi() {
        return new OpenAPI()
                .info(new Info()
                        .title("FinTrack finance-service")
                        .description("Accounts, transactions, CSV import, dashboard, shared commitments")
                        .version("v1"))
                .components(new Components().addSecuritySchemes("bearer-jwt",
                        new SecurityScheme()
                                .type(SecurityScheme.Type.HTTP)
                                .scheme("bearer")
                                .bearerFormat("JWT")))
                .addSecurityItem(new SecurityRequirement().addList("bearer-jwt"));
    }
}
