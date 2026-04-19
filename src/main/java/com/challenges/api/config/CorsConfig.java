package com.challenges.api.config;

import com.authspring.api.config.FrontendProperties;
import java.util.Arrays;
import java.util.List;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

@Configuration
public class CorsConfig {

	@Bean
	CorsConfigurationSource corsConfigurationSource(FrontendProperties frontend) {
		CorsConfiguration config = new CorsConfiguration();
		config.setAllowedOrigins(parseOrigins(frontend.baseUrl()));
		config.setAllowedMethods(List.of("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS", "HEAD"));
		config.setAllowedHeaders(
				List.of("Authorization", "Content-Type", "API-Version", "Accept", "Origin", "X-Requested-With"));
		config.setAllowCredentials(false);
		config.setMaxAge(3600L);

		UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
		source.registerCorsConfiguration("/**", config);
		return source;
	}

	private static List<String> parseOrigins(String raw) {
		if (raw == null || raw.isBlank()) {
			return List.of("http://localhost:3000");
		}
		return Arrays.stream(raw.split(","))
				.map(String::trim)
				.filter(s -> !s.isEmpty())
				.map(s -> s.replaceAll("/+$", ""))
				.toList();
	}
}
