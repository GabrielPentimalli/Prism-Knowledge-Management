package it.uniroma3.sii.service.storage;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIOException;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import com.fasterxml.jackson.databind.ObjectMapper;

class JsonStorageTest {

    @TempDir
    Path root;

    public static class BrokenValue {
        public String getValue() {
            throw new IllegalStateException("simulated serialization failure");
        }
    }

    @Test
    void failedSerializationPreservesThePreviousFileAndRemovesTemporaryFiles() throws Exception {
        Path destination = Files.writeString(root.resolve("document.json"), "{\"value\":\"original\"}");

        assertThatIOException().isThrownBy(() -> JsonStorage.write(new ObjectMapper(), destination, new BrokenValue()));

        assertThat(destination).hasContent("{\"value\":\"original\"}");
        try (var files = Files.list(root)) {
            assertThat(files.toList()).containsExactly(destination);
        }
    }

    @Test
    void completedSerializationCreatesAndReplacesReadableJson() throws Exception {
        Path destination = root.resolve("document.json");
        ObjectMapper mapper = new ObjectMapper();
        JsonStorage.write(mapper, destination, Map.of("value", "first"));
        assertThat(mapper.readTree(destination.toFile()).path("value").asText()).isEqualTo("first");

        JsonStorage.write(mapper, destination, Map.of("value", "replacement"));

        assertThat(mapper.readTree(destination.toFile()).path("value").asText()).isEqualTo("replacement");
        try (var files = Files.list(root)) {
            assertThat(files.toList()).containsExactly(destination);
        }
    }
}
