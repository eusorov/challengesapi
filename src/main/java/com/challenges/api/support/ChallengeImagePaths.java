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
