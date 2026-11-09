package it.uniroma3.sii.service.storage;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

class StorageIdTest {

    @TempDir
    Path root;

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {" ", ".", "..", "../documents", "../../", "/tmp", "a/b", "a\\b", "a\0b"})
    void rejectsPathsInsteadOfIdentifiers(String id) {
        assertThatIllegalArgumentException().isThrownBy(() -> StorageId.resolve(root, id));
    }

    @Test
    void resolvesExistingAndNewIdentifiers() throws IOException {
        Path directory = Files.createDirectory(root.resolve("doc-1"));
        assertThat(StorageId.resolve(root, "doc-1")).isEqualTo(directory);
        assertThat(StorageId.resolve(root, "new-doc")).isEqualTo(root.resolve("new-doc"));
    }

    @Test
    void rejectsSymbolicLinks() throws IOException {
        Path outside = Files.createDirectory(root.resolve("outside"));
        Path parent = Files.createDirectory(root.resolve("documents"));
        Files.createSymbolicLink(parent.resolve("linked-doc"), outside);

        assertThatIllegalArgumentException().isThrownBy(() -> StorageId.resolve(parent, "linked-doc"));
        assertThat(outside).isDirectory();
    }
}
