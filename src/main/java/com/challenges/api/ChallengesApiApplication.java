package com.challenges.api;

import com.challenges.api.config.AwsS3Properties;
import com.challenges.api.config.CheckInRetentionProperties;
import com.authspring.api.config.FrontendProperties;
import com.authspring.api.config.JwtProperties;
import com.authspring.api.config.PasswordResetMailProperties;
import com.authspring.api.config.VerificationMailProperties;
import com.authspring.api.config.VerificationProperties;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;

@SpringBootApplication(scanBasePackages = {"com.challenges.api", "com.authspring.api"})
@EnableConfigurationProperties({
	AwsS3Properties.class,
	CheckInRetentionProperties.class,
	JwtProperties.class,
	VerificationProperties.class,
	VerificationMailProperties.class,
	PasswordResetMailProperties.class,
	FrontendProperties.class
})
public class ChallengesApiApplication {

	public static void main(String[] args) {
		SpringApplication.run(ChallengesApiApplication.class, args);
	}

}
