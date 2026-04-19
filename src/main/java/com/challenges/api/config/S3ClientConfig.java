package com.challenges.api.config;

import java.net.URI;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import software.amazon.awssdk.auth.credentials.DefaultCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;

@Configuration
public class S3ClientConfig {

	@Bean
	public S3Client s3Client(AwsS3Properties props) {
		var builder = S3Client.builder()
				.region(Region.of(props.region()))
				.credentialsProvider(DefaultCredentialsProvider.create());
		String endpoint = System.getenv("AWS_S3_ENDPOINT");
		if (endpoint != null && !endpoint.isBlank()) {
			builder.endpointOverride(URI.create(endpoint));
		}
		return builder.build();
	}
}
