package com.challenges.api.storage;

import com.challenges.api.config.AwsS3Properties;
import org.springframework.stereotype.Component;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;

@Component
public class S3ChallengeImageStorage implements ChallengeImageStorage {

	private final S3Client s3;
	private final AwsS3Properties props;

	public S3ChallengeImageStorage(S3Client s3, AwsS3Properties props) {
		this.s3 = s3;
		this.props = props;
	}

	@Override
	public void putObject(String objectKey, byte[] body, String contentType) {
		s3.putObject(
				PutObjectRequest.builder()
						.bucket(props.bucket())
						.key(objectKey)
						.contentType(contentType)
						.build(),
				RequestBody.fromBytes(body));
	}
}
