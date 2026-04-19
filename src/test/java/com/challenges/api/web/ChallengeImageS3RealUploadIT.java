package com.challenges.api.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.challenges.api.config.AwsS3Properties;
import com.challenges.api.model.User;
import com.challenges.api.repo.UserRepository;
import com.challenges.api.support.JwtLoginSupport;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpHeaders;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.DeleteObjectRequest;
import software.amazon.awssdk.services.s3.model.HeadObjectRequest;

/**
 * Real S3 upload through the HTTP API. Skipped unless {@code RUN_S3_UPLOAD_IT=true}.
 *
 * <p>Requires AWS credentials in the environment (same as production: default provider chain), a
 * writable bucket, and {@code AWS_S3_BUCKET}. Optional: {@code AWS_REGION}, {@code
 * AWS_S3_PUBLIC_BASE_URL} (otherwise derived as {@code https://&lt;bucket&gt;.s3.&lt;region&gt;.amazonaws.com}).
 *
 * <p>Run:
 *
 * <pre>
 * RUN_S3_UPLOAD_IT=true AWS_S3_BUCKET=your-bucket AWS_REGION=eu-central-1 ./gradlew test --tests ChallengeImageS3RealUploadIT
 * </pre>
 */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
@EnabledIfEnvironmentVariable(named = "RUN_S3_UPLOAD_IT", matches = "true")
class ChallengeImageS3RealUploadIT {

	private static final String HV = "API-Version";
	private static final String V1 = "1";

	@DynamicPropertySource
	static void realAwsBucket(DynamicPropertyRegistry r) {
		if (!"true".equalsIgnoreCase(System.getenv("RUN_S3_UPLOAD_IT"))) {
			return;
		}
		String bucket = System.getenv("AWS_S3_BUCKET");
		if (bucket == null || bucket.isBlank()) {
			throw new IllegalStateException("AWS_S3_BUCKET must be set when RUN_S3_UPLOAD_IT=true");
		}
		String regionEnv = System.getenv("AWS_REGION");
		final String region =
				(regionEnv == null || regionEnv.isBlank()) ? "eu-central-1" : regionEnv;
		String baseEnv = System.getenv("AWS_S3_PUBLIC_BASE_URL");
		final String baseUrl =
				(baseEnv == null || baseEnv.isBlank())
						? "https://%s.s3.%s.amazonaws.com".formatted(bucket, region)
						: baseEnv;
		final String bucketFinal = bucket;
		r.add("aws.s3.bucket", () -> bucketFinal);
		r.add("aws.s3.region", () -> region);
		r.add("aws.s3.public-base-url", () -> baseUrl);
	}

	private final MockMvc mockMvc;
	private final UserRepository users;
	private final ObjectMapper objectMapper;
	private final PasswordEncoder passwordEncoder;
	private final S3Client s3;
	private final AwsS3Properties s3Props;

	private User owner;
	private String bearer;
	private long challengeId;
	private String uploadedObjectKey;

	@Autowired
	ChallengeImageS3RealUploadIT(
			MockMvc mockMvc,
			UserRepository users,
			ObjectMapper objectMapper,
			PasswordEncoder passwordEncoder,
			S3Client s3,
			AwsS3Properties s3Props) {
		this.mockMvc = mockMvc;
		this.users = users;
		this.objectMapper = objectMapper;
		this.passwordEncoder = passwordEncoder;
		this.s3 = s3;
		this.s3Props = s3Props;
	}

	@BeforeEach
	void setup() throws Exception {
		owner = users.save(JwtLoginSupport.userWithLoginPassword(passwordEncoder, "s3-it-owner@test"));
		bearer = JwtLoginSupport.bearerAuthorization(mockMvc, "s3-it-owner@test", "password");

		String body = String.format(
				"{\"ownerUserId\":%d,\"title\":\"S3 IT Challenge\",\"description\":null,"
						+ "\"startDate\":\"2026-01-01\",\"endDate\":null}",
				owner.getId());
		String created = mockMvc.perform(post("/api/challenges")
						.header(HV, V1)
						.header(HttpHeaders.AUTHORIZATION, bearer)
						.contentType(APPLICATION_JSON)
						.content(body))
				.andExpect(status().isCreated())
				.andReturn()
				.getResponse()
				.getContentAsString();
		JsonNode node = objectMapper.readTree(created);
		challengeId = node.get("id").asLong();
	}

	// @AfterEach
	void removeUploadedObjectFromS3() {
		if (uploadedObjectKey == null) {
			return;
		}
		try {
			s3.deleteObject(
					DeleteObjectRequest.builder().bucket(s3Props.bucket()).key(uploadedObjectKey).build());
		} catch (RuntimeException ignored) {
			// best-effort cleanup
		}
		uploadedObjectKey = null;
	}

	@Test
	void postChallengeImage_persistsInS3() throws Exception {
		byte[] jpegish = new byte[] {(byte) 0xff, (byte) 0xd8, (byte) 0xff, 0x01, 0x02};
		var file = new MockMultipartFile("file", "photo.jpg", "image/jpeg", jpegish);

		String responseBody = mockMvc.perform(multipart("/api/challenges/" + challengeId + "/image")
						.file(file)
						.header(HV, V1)
						.header(HttpHeaders.AUTHORIZATION, bearer))
				.andExpect(status().isOk())
				.andReturn()
				.getResponse()
				.getContentAsString();

		JsonNode body = objectMapper.readTree(responseBody);
		String key = body.get("imageObjectKey").asString();
		assertThat(key).isNotBlank();
		uploadedObjectKey = key;

		assertThat(body.get("imageUrl").asString()).contains(key);

		s3.headObject(HeadObjectRequest.builder().bucket(s3Props.bucket()).key(key).build());
	}
}
