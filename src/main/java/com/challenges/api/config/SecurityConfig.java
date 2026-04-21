package com.challenges.api.config;

import com.authspring.api.security.JwtAuthenticationFilter;
import com.authspring.api.security.ProblemJsonAuthenticationEntryPoint;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

@Configuration
@EnableWebSecurity
@EnableMethodSecurity
public class SecurityConfig {

	@Bean
	PasswordEncoder passwordEncoder() {
		return new BCryptPasswordEncoder();
	}

	@Bean
	SecurityFilterChain securityFilterChain(
			HttpSecurity http,
			JwtAuthenticationFilter jwtAuthenticationFilter,
			ProblemJsonAuthenticationEntryPoint authenticationEntryPoint)
			throws Exception {
		http.csrf(csrf -> csrf.disable());
		http.cors(Customizer.withDefaults());
		http.sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS));
		http.authorizeHttpRequests(auth -> auth
				.requestMatchers(HttpMethod.OPTIONS, "/**").permitAll()
				.requestMatchers("/actuator/**").permitAll()
				.requestMatchers(
						"/swagger-ui.html",
						"/swagger-ui/**",
						"/v3/api-docs",
						"/v3/api-docs.yaml",
						"/v3/api-docs/**")
						.permitAll()
				.requestMatchers(
						HttpMethod.POST,
						"/api/login",
						"/api/login/",
						"/api/register",
						"/api/register/",
						"/api/forgot-password",
						"/api/forgot-password/",
						"/api/reset-password",
						"/api/reset-password/")
						.permitAll()
				.requestMatchers(HttpMethod.GET, "/api/email/verify/**").permitAll()
				.requestMatchers(HttpMethod.POST, "/api/users", "/api/users/").permitAll()
				.requestMatchers(HttpMethod.GET, "/api/categories", "/api/categories/").permitAll()
				.requestMatchers(HttpMethod.GET, "/api/challenges/mine", "/api/challenges/mine/")
						.authenticated()
				.requestMatchers(HttpMethod.GET, "/api/challenges", "/api/challenges/").permitAll()
				.requestMatchers(
						HttpMethod.GET,
						"/api/challenges/{id:\\d+}",
						"/api/challenges/{id:\\d+}/",
						"/api/challenges/{id:\\d+}/subtasks",
						"/api/challenges/{id:\\d+}/subtasks/",
						"/api/challenges/{id:\\d+}/participants",
						"/api/challenges/{id:\\d+}/participants/")
						.permitAll()
				.requestMatchers(HttpMethod.GET, "/api/subtasks/{id:\\d+}", "/api/subtasks/{id:\\d+}/")
						.permitAll()
				.requestMatchers("/api/**").authenticated()
				.anyRequest().permitAll());
		http.exceptionHandling(ex -> ex.authenticationEntryPoint(authenticationEntryPoint));
		http.addFilterBefore(jwtAuthenticationFilter, UsernamePasswordAuthenticationFilter.class);
		return http.build();
	}
}
