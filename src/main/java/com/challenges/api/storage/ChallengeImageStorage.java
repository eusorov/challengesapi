package com.challenges.api.storage;

import software.amazon.awssdk.services.s3.model.PutObjectResponse;

public interface ChallengeImageStorage {

	PutObjectResponse putObject(String objectKey, byte[] body, String contentType);
}
