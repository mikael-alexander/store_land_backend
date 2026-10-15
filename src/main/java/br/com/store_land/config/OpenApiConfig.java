package br.com.store_land.config;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import org.springdoc.core.customizers.OpenApiCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.List;

@Configuration
public class OpenApiConfig {

    @Bean
    OpenAPI storeLandOpenApi() {
        return new OpenAPI()
                .info(new Info()
                        .title("Store Land API")
                        .description("""
                                API de autenticação do Store Land.

                                Para endpoints POST, primeiro chame GET /api/auth/csrf.
                                Envie o token retornado no cabeçalho X-XSRF-TOKEN e
                                mantenha os cookies recebidos, incluindo JSESSIONID.
                                """)
                        .version("v1"))
                .components(new Components()
                        .addSecuritySchemes("sessionCookie",
                                new SecurityScheme()
                                        .type(SecurityScheme.Type.APIKEY)
                                        .in(SecurityScheme.In.COOKIE)
                                        .name("JSESSIONID")
                                        .description("Cookie de sessão criado após autenticação."))
                        .addSecuritySchemes("csrfToken",
                                new SecurityScheme()
                                        .type(SecurityScheme.Type.APIKEY)
                                        .in(SecurityScheme.In.HEADER)
                                        .name("X-XSRF-TOKEN")
                                        .description("Token obtido em GET /api/auth/csrf.")));
    }

    @Bean
    OpenApiCustomizer logoutSecurityRequirements() {
        return openApi -> {
            var logoutPath = openApi.getPaths().get("/api/auth/logout");
            if (logoutPath != null && logoutPath.getPost() != null) {
                logoutPath.getPost().setSecurity(List.of(
                        new SecurityRequirement()
                                .addList("sessionCookie")
                                .addList("csrfToken")
                ));
            }
        };
    }
}
