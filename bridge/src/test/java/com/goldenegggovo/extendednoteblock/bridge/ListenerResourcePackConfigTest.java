package com.goldenegggovo.extendednoteblock.bridge;

import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.*;

class ListenerResourcePackConfigTest {
    private static final String ID = "4ac1cf2c-ea6d-52a8-9605-8ecff682bd8d";
    private static final String URL = "https://example.com/enb.zip";
    private static final String SHA1 = "0123456789abcdef0123456789abcdef01234567";

    @Test void officialMetadataWinsOverStaleServerConfig() throws Exception {
        String properties = "id=" + ID + "\nurl=" + URL + "\nsha1=" + SHA1 + "\n";
        var resolved = ListenerResourcePackConfig.resolve(true,
                new ByteArrayInputStream(properties.getBytes(StandardCharsets.UTF_8)),
                ID, "https://example.com/old.zip", "f".repeat(40));

        assertEquals(URL, resolved.url());
        assertEquals(SHA1, resolved.sha1());
        assertEquals("official release", resolved.source());
        assertEquals(20, resolved.sha1Bytes().length);
    }

    @Test void customMetadataIsUsedOnlyWhenExplicitlySelected() throws Exception {
        var resolved = ListenerResourcePackConfig.resolve(false, null, ID, URL, SHA1.toUpperCase());
        assertEquals(URL, resolved.url());
        assertEquals(SHA1, resolved.sha1());
        assertEquals("custom config", resolved.source());
    }

    @Test void placeholdersMissingMetadataAndUnsafeValuesAreRejected() {
        assertThrows(IllegalArgumentException.class,
                () -> ListenerResourcePackConfig.resolve(true, null, ID, URL, SHA1));
        String placeholders = "id=" + ID + "\nurl=__ENB_RESOURCE_PACK_URL__\nsha1=__ENB_RESOURCE_PACK_SHA1__\n";
        assertThrows(IllegalArgumentException.class,
                () -> ListenerResourcePackConfig.resolve(true,
                        new ByteArrayInputStream(placeholders.getBytes(StandardCharsets.UTF_8)), ID, URL, SHA1));
        assertThrows(IllegalArgumentException.class,
                () -> ListenerResourcePackConfig.resolve(false, null, ID, "http://example.com/enb.zip", SHA1));
        assertThrows(IllegalArgumentException.class,
                () -> ListenerResourcePackConfig.resolve(false, null, ID, URL, "bad"));
    }
}
