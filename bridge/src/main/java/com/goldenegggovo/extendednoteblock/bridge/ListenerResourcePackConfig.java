package com.goldenegggovo.extendednoteblock.bridge;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.Properties;
import java.util.UUID;

/** Resolves either the release-managed listener pack or an explicitly configured custom pack. */
final class ListenerResourcePackConfig {
    private static final String PLACEHOLDER_PREFIX = "__ENB_";

    private ListenerResourcePackConfig() {
    }

    static Resolved resolve(boolean useOfficialRelease, InputStream officialMetadata,
                            String customId, String customUrl, String customSha1) throws IOException {
        String id = customId;
        String url = customUrl;
        String sha1 = customSha1;
        String source = "custom config";

        if (useOfficialRelease) {
            if (officialMetadata == null) {
                throw new IllegalArgumentException("missing embedded release-pack metadata");
            }
            Properties official = new Properties();
            official.load(new InputStreamReader(officialMetadata, StandardCharsets.UTF_8));
            id = official.getProperty("id", "");
            url = official.getProperty("url", "");
            sha1 = official.getProperty("sha1", "");
            source = "official release";
        }

        id = id == null ? "" : id.trim();
        url = url == null ? "" : url.trim();
        sha1 = sha1 == null ? "" : sha1.trim().toLowerCase(java.util.Locale.ROOT);
        if (id.isBlank() || url.isBlank() || sha1.isBlank()
                || id.startsWith(PLACEHOLDER_PREFIX) || url.startsWith(PLACEHOLDER_PREFIX)
                || sha1.startsWith(PLACEHOLDER_PREFIX)) {
            throw new IllegalArgumentException(source + " resource-pack metadata is not configured");
        }

        UUID uuid = UUID.fromString(id);
        URI parsed = URI.create(url);
        if (!"https".equalsIgnoreCase(parsed.getScheme()) || parsed.getHost() == null
                || !StandardCharsets.US_ASCII.newEncoder().canEncode(url)) {
            throw new IllegalArgumentException("resource-pack.url must be an ASCII HTTPS URL");
        }
        if (!sha1.matches("(?i)[0-9a-f]{40}")) {
            throw new IllegalArgumentException("resource-pack.sha1 must contain exactly 40 hexadecimal characters");
        }
        return new Resolved(uuid, url, sha1, source);
    }

    record Resolved(UUID id, String url, String sha1, String source) {
        byte[] sha1Bytes() {
            byte[] decoded = new byte[20];
            for (int i = 0; i < decoded.length; i++) {
                decoded[i] = (byte) Integer.parseInt(sha1.substring(i * 2, i * 2 + 2), 16);
            }
            return decoded;
        }
    }
}
