# Challenge image upload to Amazon S3 Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Let an authenticated user **upload one image per challenge**; the object is stored in Amazon S3 under bucket **`challanges`** (per product spelling), with object key **`{challengeFolder}/{imageFileName}`** where **`challengeFolder`** is derived from the challenge’s **name (title)** and **`imageFileName`** is a safe, unique file name. Persist the **S3 object key** on the challenge for later display/API responses. **No other new challenge endpoints** beyond what is required for upload (and optional read of URL/key in existing `ChallengeResponse`).

**Architecture:** Add **`image_object_key`** (nullable `VARCHAR`) on **`challenges`** via Flyway. Introduce **`AwsS3Properties`** (`bucket`, `region`) and a thin **`S3ObjectStorage`** adapter (AWS SDK v2 `S3Client`) behind a small **`ChallengeImageStorage`** port (interface) for tests. **`ChallengeService`** gains **`uploadImage(challengeId, multipartFile, currentUserId)`**: load challenge, **403** if caller is not **`owner_user_id`**, validate content type/size, compute **`folder = slugify(title) + "-" + id`** (avoids empty or colliding folders), **`fileName = sanitizedOriginal + "-" + shortUuid + extension`**, **`PutObject`**, save key on entity. Expose **`POST /api/challenges/{id}/image`** (`multipart/form-data`, field name **`file`**) on **`ChallengeController`**; requires JWT like other `/api/**` routes. Extend **`ChallengeResponse`** with optional **`imageObjectKey`** and **`imageUrl`** (HTTPS URL to the object for public buckets, or document presigned-URL follow-up).

**Tech stack:** Java 25, Spring Boot 4.0.x, Spring Security + JWT, **AWS SDK for Java 2.x** (`software.amazon.awssdk:s3`), Flyway, PostgreSQL, JUnit 5 + Mockito.

**Bucket naming note:** S3 bucket names must be **globally unique** and **DNS-compliant** (lowercase). The name **`challanges`** is valid as a label; in production you may need a **suffix** (e.g. `challanges-prod-acctid`). The plan uses **`${AWS_S3_BUCKET:challanges}`** so environments can override.

---

## File map

| Path | Responsibility |
|------|----------------|
| `build.gradle` | AWS SDK BOM + `s3` dependency; optional `testImplementation` for **LocalStack** only if you add Task 5b. |
| `src/main/resources/application.yml` | `aws.s3.bucket`, `aws.s3.region`; `spring.servlet.multipart.max-file-size` / `max-request-size` (e.g. **5MB**). |
| `src/test/resources/application-test.yml` | Same keys with dummy bucket/region **or** `test` profile **`ChallengeImageStorage`** `@MockBean` / no-op. |
| `src/main/resources/db/migration/V12__challenge_image.sql` | `ALTER TABLE challenges ADD COLUMN image_object_key VARCHAR(1024) NULL;` |
| `src/main/java/com/challenges/api/model/Challenge.java` | Field **`imageObjectKey`** + getter/setter; **not** `unique` (one row = one challenge). |
| `src/main/java/com/challenges/api/config/AwsS3Properties.java` | `@ConfigurationProperties(prefix = "aws.s3")` record `bucket`, `region`. |
| `src/main/java/com/challenges/api/config/S3ClientConfig.java` | `@Bean S3Client` from region + `DefaultCredentialsProvider.create()`. |
| `src/main/java/com/challenges/api/storage/ChallengeImageStorage.java` | Interface: `void put(String objectKey, byte[] bytes, String contentType)`. |
| `src/main/java/com/challenges/api/storage/S3ChallengeImageStorage.java` | Implements with `PutObject`; uses **`AwsS3Properties`**. |
| `src/main/java/com/challenges/api/support/ChallengeImagePaths.java` | Static helpers: **`folderSegment(Challenge)`**, **`safeImageFileName(String originalFilename)`**. |
| `src/main/java/com/challenges/api/service/ChallengeService.java` | **`uploadChallengeImage(...)`** with owner check, validation, delegate storage, **`save`**. |
| `src/main/java/com/challenges/api/web/ChallengeController.java` | **`POST .../{id}/image`** with **`@RequestParam("file") MultipartFile`**, **`@AuthenticationPrincipal UserPrincipal`**. |
| `src/main/java/com/challenges/api/web/dto/ChallengeResponse.java` | Add **`imageObjectKey`**, **`imageUrl`** (nullable); `from(Challenge, Optional<String> publicBaseUrl)` or build URL in service — keep **single** `from` by reading a small **`@Value`** helper or pass **`ImageUrlBuilder`** — **simplest:** add optional components nullable from entity only + static base URL from properties injected in controller when mapping (plan uses **two** record factories or **overloaded** `from` — pick one in Task 4). |
| `src/test/java/com/challenges/api/service/ChallengeServiceImageUploadTest.java` | Mockito: mock **`ChallengeImageStorage`**, verify **`put`** key shape and **`save`**. |
| `src/test/java/com/challenges/api/web/ChallengeImageUploadIT.java` | **`@SpringBootTest`**, login as owner, **`multipart`** POST, assert **200** and JSON contains key/URL; **403** for non-owner (second user). **Requires** S3 reachable or **`@MockBean ChallengeImageStorage`** that no-ops and asserts call — **prefer `@MockBean`** for CI without AWS. |

---

### Task 1: Flyway + `Challenge` entity

**Files:**
- Create: `src/main/resources/db/migration/V12__challenge_image.sql`
- Modify: `src/main/java/com/challenges/api/model/Challenge.java`

- [x] **Step 1: Migration**

```sql
ALTER TABLE challenges
    ADD COLUMN image_object_key VARCHAR(1024) NULL;
```

- [x] **Step 2: Entity field**

Add to `Challenge`:

```java
@Column(name = "image_object_key", length = 1024)
private String imageObjectKey;

public String getImageObjectKey() {
	return imageObjectKey;
}

public void setImageObjectKey(String imageObjectKey) {
	this.imageObjectKey = imageObjectKey;
}
```

- [x] **Step 3: Run Flyway on `challengestest`**

After `./gradlew test`, **`prepareTestDatabase`** + migrate should apply **V12**. If checksum drift: repair or clean test DB.

- [x] **Step 4: Commit**

```bash
git add src/main/resources/db/migration/V12__challenge_image.sql \
  src/main/java/com/challenges/api/model/Challenge.java
git commit -m "feat(challenge): image_object_key column"
```

---

### Task 2: AWS config + `S3Client` + storage adapter

**Files:**
- Modify: `build.gradle`
- Create: `src/main/java/com/challenges/api/config/AwsS3Properties.java`
- Create: `src/main/java/com/challenges/api/config/S3ClientConfig.java`
- Create: `src/main/java/com/challenges/api/storage/ChallengeImageStorage.java`
- Create: `src/main/java/com/challenges/api/storage/S3ChallengeImageStorage.java`
- Modify: `src/main/java/com/challenges/api/ChallengesApiApplication.java` — add `@EnableConfigurationProperties(AwsS3Properties.class)` if not using a separate `@Configuration`.

- [x] **Step 1: Gradle**

In `build.gradle` `dependencies` block:

```gradle
implementation platform('software.amazon.awssdk:bom:2.29.51')
implementation 'software.amazon.awssdk:s3'
```

- [x] **Step 2: `AwsS3Properties`**

```java
package com.challenges.api.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "aws.s3")
public record AwsS3Properties(String bucket, String region) {}
```

- [x] **Step 3: `S3ClientConfig`**

```java
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
```

(`AWS_S3_ENDPOINT` optional for **LocalStack** / MinIO in dev.)

- [x] **Step 4: Port + implementation**

`ChallengeImageStorage.java`:

```java
package com.challenges.api.storage;

public interface ChallengeImageStorage {

	void putObject(String objectKey, byte[] body, String contentType);
}
```

`S3ChallengeImageStorage.java`:

```java
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
```

- [x] **Step 5: `application.yml`**

```yaml
aws:
  s3:
    bucket: ${AWS_S3_BUCKET:challanges}
    region: ${AWS_REGION:eu-west-1}

spring:
  servlet:
    multipart:
      max-file-size: 5MB
      max-request-size: 5MB
```

- [x] **Step 6: `./gradlew compileJava --no-daemon`** → **BUILD SUCCESSFUL**

- [x] **Step 7: Commit** `feat(storage): S3 client and challenge image put adapter`

---

### Task 3: Path helpers + `ChallengeService.uploadChallengeImage`

**Files:**
- Create: `src/main/java/com/challenges/api/support/ChallengeImagePaths.java`
- Modify: `src/main/java/com/challenges/api/service/ChallengeService.java`

- [x] **Step 1: Path helpers**

```java
package com.challenges.api.support;

import com.challenges.api.model.Challenge;
import java.text.Normalizer;
import java.util.Locale;
import java.util.UUID;

public final class ChallengeImagePaths {

	private ChallengeImagePaths() {}

	public static String folderSegment(Challenge challenge) {
		String slug = slugify(challenge.getTitle());
		if (slug.isEmpty()) {
			slug = "challenge";
		}
		return slug + "-" + challenge.getId();
	}

	public static String safeImageFileName(String originalFilename) {
		String name = originalFilename == null ? "" : originalFilename.replace("\\", "/");
		int slash = name.lastIndexOf('/');
		if (slash >= 0) {
			name = name.substring(slash + 1);
		}
		if (name.isBlank()) {
			name = "image";
		}
		name = name.replaceAll("[^a-zA-Z0-9._-]", "_");
		int dot = name.lastIndexOf('.');
		String ext = dot > 0 ? name.substring(dot) : "";
		String base = dot > 0 ? name.substring(0, dot) : name;
		if (base.length() > 100) {
			base = base.substring(0, 100);
		}
		String suffix = UUID.randomUUID().toString().substring(0, 8);
		return base + "-" + suffix + ext;
	}

	public static String objectKey(Challenge challenge, String originalFilename) {
		return folderSegment(challenge) + "/" + safeImageFileName(originalFilename);
	}

	private static String slugify(String title) {
		String s = Normalizer.normalize(title, Normalizer.Form.NFD).replaceAll("\\p{M}", "");
		s = s.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]+", "-").replaceAll("(^-|-$)", "");
		return s.length() > 200 ? s.substring(0, 200) : s;
	}
}
```

- [x] **Step 2: Service method** (inject **`ChallengeImageStorage`**; allowed types e.g. **`image/jpeg`**, **`image/png`**, **`image/webp`**; max size enforced by Spring + assert `file.getSize() > 0`)

```java
@Transactional
public Optional<Challenge> uploadImage(
		@NonNull Long challengeId,
		@NonNull org.springframework.web.multipart.MultipartFile file,
		long currentUserId) {
	Assert.notNull(challengeId, "challengeId must not be null");
	Assert.notNull(file, "file must not be null");
	return challenges.findByIdWithOwner(challengeId).map(ch -> {
		if (!ch.getOwner().getId().equals(currentUserId)) {
			throw new org.springframework.security.access.AccessDeniedException("Not challenge owner");
		}
		String contentType = file.getContentType();
		if (contentType == null
				|| !(contentType.equals("image/jpeg")
						|| contentType.equals("image/png")
						|| contentType.equals("image/webp"))) {
			throw new IllegalArgumentException("Only image/jpeg, image/png, image/webp allowed");
		}
		String key = com.challenges.api.support.ChallengeImagePaths.objectKey(ch, file.getOriginalFilename());
		try {
			byte[] bytes = file.getBytes();
			if (bytes.length == 0) {
				throw new IllegalArgumentException("Empty file");
			}
			challengeImageStorage.putObject(key, bytes, contentType);
		} catch (java.io.IOException e) {
			throw new IllegalStateException(e);
		}
		ch.setImageObjectKey(key);
		return challenges.save(ch);
	});
}
```

Add field `private final ChallengeImageStorage challengeImageStorage;` and constructor parameter.

- [x] **Step 3: Unit test `ChallengeServiceImageUploadTest`** — mock **`ChallengeImageStorage`**, stub **`ChallengeRepository.findByIdWithOwner`**, assert **`putObject`** receives key matching **`summer-challenge-42/photo-xxxxxxxx.jpg`** pattern (regex), owner mismatch → **`AccessDeniedException`**.

- [x] **Step 4: `./gradlew test --tests '*ChallengeServiceImageUploadTest' --no-daemon`** → **PASS**

- [x] **Step 5: Commit** `feat(challenge): upload image to S3 and save object key`

---

### Task 4: REST `POST /api/challenges/{id}/image` + response fields

**Files:**
- Modify: `src/main/java/com/challenges/api/web/ChallengeController.java`
- Modify: `src/main/java/com/challenges/api/web/dto/ChallengeResponse.java`
- Modify: `src/main/java/com/challenges/api/web/GlobalExceptionHandler.java` (optional) — map **`AccessDeniedException`** → **403 ProblemDetail**; **`IllegalArgumentException`** → **400**.

- [x] **Step 1: Extend `ChallengeResponse`**

Add nullable **`String imageObjectKey`**, **`String imageUrl`** as the last two record components. Add factories:

```java
public static ChallengeResponse from(Challenge c) {
	return from(c, null);
}

public static ChallengeResponse from(Challenge c, String imagePublicBaseUrl) {
	String key = c.getImageObjectKey();
	String url = null;
	if (key != null && imagePublicBaseUrl != null && !imagePublicBaseUrl.isBlank()) {
		url = imagePublicBaseUrl.replaceAll("/$", "") + "/" + key;
	}
	return new ChallengeResponse(
			c.getId(),
			c.getOwner().getId(),
			c.getTitle(),
			c.getDescription(),
			c.getStartDate(),
			c.getEndDate(),
			c.getCreatedAt(),
			key,
			url);
}
```

In **`ChallengeController`**, inject **`@Value("${aws.s3.public-base-url:}") String imagePublicBaseUrl`** and use **`ChallengeResponse.from(ch, imagePublicBaseUrl)`** for **`list`**, **`get`**, **`create`**, **`replace`**, and **`uploadImage`** so **`imageUrl`** is populated when the base URL is configured.

- [x] **Step 2: Controller endpoint**

```java
import com.authspring.api.security.UserPrincipal;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.multipart.MultipartFile;

@PostMapping("/{id}/image")
public ResponseEntity<ChallengeResponse> uploadImage(
		@PathVariable Long id,
		@RequestParam("file") MultipartFile file,
		@AuthenticationPrincipal UserPrincipal principal) {
	return challengeService
			.uploadImage(id, file, principal.getId())
			.map(ch -> ResponseEntity.ok(ChallengeResponse.from(ch, imagePublicBaseUrl)))
			.orElse(ResponseEntity.notFound().build());
}
```

Add **`private final String imagePublicBaseUrl;`** from **`@Value("${aws.s3.public-base-url:}")`** constructor injection.

`application.yml`:

```yaml
aws:
  s3:
    public-base-url: ${AWS_S3_PUBLIC_BASE_URL:}
```

If empty, **`imageUrl`** in JSON is **`null`**; clients can still build URL for public buckets as **`https://challanges.s3.eu-west-1.amazonaws.com/{key}`** if policy allows.

- [x] **Step 3: Integration test `ChallengeImageUploadIT`**

- Create two users; create challenge as user A; login A → **`POST /api/challenges/{id}/image`** with **`multipartFile`** → **200**, **`$.imageObjectKey`** matches regex **`^[a-z0-9-]+-\\d+/[a-zA-Z0-9._-]+\\.[a-z]+$`** (adjust if no extension).
- Login B → same POST → **403**.
- **`@MockitoBean ChallengeImageStorage`** (Spring Boot 4; replaces deprecated `@MockBean`) so CI does not call real AWS.

- [x] **Step 4: `./gradlew test --no-daemon`** → **BUILD SUCCESSFUL**

- [x] **Step 5: Commit** `feat(api): POST challenge image upload`

---

## Self-review

1. **Spec coverage:** Upload image, S3 bucket **`challanges`**, key **`{challengeNameFolder}/{imageFileName}`** — **Task 1–4** implement with slug+id folder and safe filename. **Owner-only** enforced.
2. **Placeholder scan:** No TBD; concrete SQL, Java, Gradle, YAML included.
3. **Type consistency:** **`ChallengeService.uploadImage`** returns **`Optional<Challenge>`** consistent with **`findById`**. **`UserPrincipal.getId()`** matches **`owner_user_id`** check.

---

**Plan complete and saved to `docs/superpowers/plans/2026-04-19-07-challenge-s3-image-upload.md`. Two execution options:**

**1. Subagent-Driven (recommended)** — fresh subagent per task, review between tasks.

**2. Inline Execution** — execute tasks in this session using executing-plans with checkpoints.

**Which approach?**
