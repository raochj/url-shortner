package com.schwab.urlshortener.config;

import io.swagger.v3.oas.annotations.OpenAPIDefinition;
import io.swagger.v3.oas.annotations.enums.SecuritySchemeIn;
import io.swagger.v3.oas.annotations.enums.SecuritySchemeType;
import io.swagger.v3.oas.annotations.info.Contact;
import io.swagger.v3.oas.annotations.info.Info;
import io.swagger.v3.oas.annotations.security.SecurityScheme;
import io.swagger.v3.oas.annotations.servers.Server;
import io.swagger.v3.oas.models.OpenAPI;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
@OpenAPIDefinition(
    info =
        @Info(
            title = "Schwab URL Shortener API",
            version = "1.0.0",
            description = "Schwab URL Shortener with Agentic Orchestration Engine",
            contact = @Contact(name = "Schwab Engineering", email = "engineering@schwab.com")),
    servers = {@Server(url = "http://localhost:8080", description = "Local Development Server")})
@SecurityScheme(
    name = "BearerAuth",
    type = SecuritySchemeType.HTTP,
    scheme = "bearer",
    bearerFormat = "JWT",
    in = SecuritySchemeIn.HEADER,
    description = "JWT Bearer token authentication")
public class OpenApiConfig {
  @Bean
  public OpenAPI openAPI() {
    return new OpenAPI();
  }
}
