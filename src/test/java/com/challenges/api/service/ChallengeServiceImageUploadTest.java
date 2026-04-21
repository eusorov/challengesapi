package com.challenges.api.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.challenges.api.model.Challenge;
import com.challenges.api.model.User;
import com.challenges.api.repo.ChallengeRepository;
import com.challenges.api.repo.ParticipantRepository;
import com.challenges.api.repo.UserRepository;
import com.challenges.api.storage.ChallengeImageStorage;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.web.multipart.MultipartFile;
import software.amazon.awssdk.http.SdkHttpResponse;
import software.amazon.awssdk.services.s3.model.PutObjectResponse;

@ExtendWith(MockitoExtension.class)
class ChallengeServiceImageUploadTest {

	@Mock
	private UserRepository users;

	@Mock
	private ChallengeRepository challenges;

	@Mock
	private ParticipantRepository participants;

	@Mock
	private ChallengeImageStorage challengeImageStorage;

	@Mock
	private InviteService inviteService;

	private ChallengeService service;

	@BeforeEach
	void setUp() {
		service = new ChallengeService(users, challenges, participants, challengeImageStorage, inviteService);
	}

	@Test
	void uploadImage_owner_putsObjectWithExpectedKeyShape() throws Exception {
		PutObjectResponse putOk = mock(PutObjectResponse.class);
		SdkHttpResponse httpOk = mock(SdkHttpResponse.class);
		when(httpOk.isSuccessful()).thenReturn(true);
		when(putOk.sdkHttpResponse()).thenReturn(httpOk);
		when(challengeImageStorage.putObject(any(), any(), any())).thenReturn(putOk);

		User owner = mock(User.class);
		when(owner.getId()).thenReturn(1L);
		Challenge ch = mock(Challenge.class);
		when(ch.getOwner()).thenReturn(owner);
		when(ch.getTitle()).thenReturn("Summer Challenge");
		when(ch.getId()).thenReturn(42L);

		when(challenges.findByIdWithSubtasksAndOwner(42L)).thenReturn(Optional.of(ch));
		when(challenges.save(ch)).thenReturn(ch);

		MultipartFile file = mock(MultipartFile.class);
		when(file.getContentType()).thenReturn("image/jpeg");
		when(file.getOriginalFilename()).thenReturn("photo.jpg");
		when(file.getBytes()).thenReturn(new byte[] {1, 2, 3});

		Optional<Challenge> out = service.uploadImage(42L, file, 1L);

		assertThat(out).isPresent();
		ArgumentCaptor<String> keyCaptor = ArgumentCaptor.forClass(String.class);
		verify(challengeImageStorage).putObject(keyCaptor.capture(), any(), eq("image/jpeg"));
		assertThat(keyCaptor.getValue()).matches("summer-challenge-42/photo-[a-f0-9]{8}\\.jpg");
		verify(ch).setImageObjectKey(keyCaptor.getValue());
	}

	@Test
	void uploadImage_nonOwner_throwsAccessDenied() throws Exception {
		User owner = mock(User.class);
		when(owner.getId()).thenReturn(1L);
		Challenge ch = mock(Challenge.class);
		when(ch.getOwner()).thenReturn(owner);

		when(challenges.findByIdWithSubtasksAndOwner(42L)).thenReturn(Optional.of(ch));

		assertThatThrownBy(() -> service.uploadImage(42L, mock(MultipartFile.class), 99L))
				.isInstanceOf(AccessDeniedException.class);
	}
}
