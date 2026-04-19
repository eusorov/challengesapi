package com.challenges.api.config;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.security.SecurityScheme;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class OpenApiConfig {

	@Bean
	public OpenAPI challengesOpenApi() {
		return new OpenAPI()
				.info(new Info()
						.title("Challenges API")
						.version("1")
						.description(
								"JSON REST API for challenges and authentication. "
										+ "Include header `API-Version: 1` on API requests where controllers declare versioning."))
				.components(new Components()
						.addSecuritySchemes(
								"bearer-jwt",
								new SecurityScheme()
										.type(SecurityScheme.Type.HTTP)
										.scheme("bearer")
										.bearerFormat("JWT")
										.description("JWT returned by POST /api/login or POST /api/register.")));
	}
}
