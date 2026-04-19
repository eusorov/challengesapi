package com.challenges.api.storage;

public interface ChallengeImageStorage {

	void putObject(String objectKey, byte[] body, String contentType);
}
