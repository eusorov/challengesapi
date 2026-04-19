package com.challenges.api.web;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.ApiVersionConfigurer;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
public class ApiVersionWebConfiguration implements WebMvcConfigurer {

	@Override
	public void configureApiVersioning(ApiVersionConfigurer configurer) {
		// Header still preferred; when absent (e.g. curl), default matches @RequestMapping(version = "1").
		configurer.useRequestHeader("API-Version").setDefaultVersion("1");
	}
}
