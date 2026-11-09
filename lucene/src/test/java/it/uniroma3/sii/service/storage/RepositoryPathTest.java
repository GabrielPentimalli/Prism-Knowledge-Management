package it.uniroma3.sii.service.storage;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import com.fasterxml.jackson.databind.ObjectMapper;

import it.uniroma3.sii.model.ChatScopeType;

class RepositoryPathTest {

    @TempDir
    Path root;

    private DocumentRepository documents;
    private VaultRepository vaults;
    private ChatSessionRepository chats;

    @BeforeEach
    void setUp() {
        StoragePathService paths = mock(StoragePathService.class);
        when(paths.documentsDir()).thenReturn(root.resolve("documents"));
        when(paths.vaultsDir()).thenReturn(root.resolve("vaults"));
        when(paths.chatsDir()).thenReturn(root.resolve("chats"));
        ObjectMapper mapper = new ObjectMapper().findAndRegisterModules();
        documents = new DocumentRepository(paths, mapper);
        vaults = new VaultRepository(paths, mapper);
        chats = new ChatSessionRepository(paths, mapper);
    }

    @Test
    void deletionCannotEscapeStorageDirectories() throws IOException {
        Path victim = Files.createDirectories(root.resolve("outside"));
        Path data = Files.writeString(victim.resolve("keep.txt"), "keep");
        Files.createDirectories(root.resolve("documents"));
        Files.createDirectories(root.resolve("vaults"));
        Files.createDirectories(root.resolve("chats/document"));

        assertThatIllegalArgumentException().isThrownBy(() -> documents.deleteDocument("../outside"));
        assertThatIllegalArgumentException().isThrownBy(() -> vaults.delete("../outside"));
        assertThatIllegalArgumentException().isThrownBy(() -> chats.deleteScope(ChatScopeType.DOCUMENT, "../../outside"));
        assertThat(data).hasContent("keep");
    }

    @Test
    void readsAndChatCreationRejectAbsoluteOrTraversingIdentifiers() {
        assertThatIllegalArgumentException().isThrownBy(() -> documents.readChunks(root.toString()));
        assertThatIllegalArgumentException().isThrownBy(() -> vaults.findById(".."));
        assertThatIllegalArgumentException().isThrownBy(() -> chats.list(ChatScopeType.VAULT, "../document"));
        assertThatIllegalArgumentException().isThrownBy(() -> chats.create(ChatScopeType.DOCUMENT, "../document"));
    }
}
