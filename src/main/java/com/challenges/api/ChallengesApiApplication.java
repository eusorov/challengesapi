package com.challenges.api;

import com.authspring.api.config.JwtProperties;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;

@SpringBootApplication(scanBasePackages = {"com.challenges.api", "com.authspring.api"})
@EnableConfigurationProperties(JwtProperties.class)
public class ChallengesApiApplication {

	public static void main(String[] args) {
		SpringApplication.run(ChallengesApiApplication.class, args);
	}

}
