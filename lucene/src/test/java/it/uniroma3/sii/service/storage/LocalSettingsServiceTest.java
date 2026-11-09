package it.uniroma3.sii.service.storage;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import com.fasterxml.jackson.databind.ObjectMapper;

import it.uniroma3.sii.dto.OnboardingInitRequest;
import it.uniroma3.sii.dto.SettingsUpdateRequest;

class LocalSettingsServiceTest {

    @TempDir
    Path root;

    private LocalSettingsService service() {
        return new LocalSettingsService(new ObjectMapper().findAndRegisterModules(),
                root.resolve("data").toString(), root.resolve("bootstrap.json").toString(),
                "http://localhost:11434/v1", "original");
    }

    @Test
    void invalidUpdateDoesNotPersistOrCacheRejectedSettings() throws Exception {
        LocalSettingsService settings = service();
        settings.getSettings();
        Path configFile = settings.settingsFileInDataRoot();
        String before = Files.readString(configFile);
        String bootstrapBefore = Files.readString(root.resolve("bootstrap.json"));
        SettingsUpdateRequest update = new SettingsUpdateRequest();
        update.setLlmBaseUrl("http://example.org/v1");
        update.setLlmModel("rejected");
        update.setDataRoot(root.resolve("rejected-data").toString());

        assertThatIllegalArgumentException().isThrownBy(() -> settings.update(update));

        assertThat(Files.readString(configFile)).isEqualTo(before);
        assertThat(Files.readString(root.resolve("bootstrap.json"))).isEqualTo(bootstrapBefore);
        assertThat(root.resolve("rejected-data")).doesNotExist();
        assertThat(settings.getSettings().getLlmModel()).isEqualTo("original");
        assertThat(service().getSettings().getLlmBaseUrl()).isEqualTo("http://localhost:11434/v1");
    }

    @Test
    void invalidOnboardingDoesNotMarkSetupCompleted() {
        LocalSettingsService settings = service();
        OnboardingInitRequest request = new OnboardingInitRequest();
        request.setDataRoot(root.resolve("data").toString());
        request.setLlmBaseUrl("ftp://localhost/v1");

        assertThatIllegalArgumentException().isThrownBy(() -> settings.initialize(request));

        assertThat(settings.isOnboardingCompleted()).isFalse();
    }
}
