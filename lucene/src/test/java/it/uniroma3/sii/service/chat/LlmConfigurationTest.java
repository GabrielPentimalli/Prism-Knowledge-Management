package it.uniroma3.sii.service.chat;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class LlmConfigurationTest {

    @ParameterizedTest
    @ValueSource(strings = {"http://localhost:11434/v1", "http://127.0.0.1:11434/v1", "http://[::1]:11434/v1"})
    void acceptsLocalHttpEndpointsIncludingIpv6(String endpoint) {
        assertThatCode(() -> LlmClient.validateConfiguration(endpoint, "model")).doesNotThrowAnyException();
    }

    @ParameterizedTest
    @ValueSource(strings = {"http://example.org/v1", "ftp://localhost/v1", "//localhost/v1", "http://localhost.example.org/v1"})
    void rejectsRemoteHostsAndUnsupportedSchemes(String endpoint) {
        assertThatIllegalArgumentException().isThrownBy(() -> LlmClient.validateConfiguration(endpoint, "model"));
    }

    @Test
    void rejectedConfigurationKeepsThePreviousClient() {
        LlmClient client = new LlmClient("http://localhost:11434/v1", "original");

        assertThatIllegalArgumentException().isThrownBy(() -> client.configure("http://example.org/v1", "other"));

        assertThat(client.getModel()).isEqualTo("original");
        assertThat(client.getBaseUrl()).isEqualTo("http://localhost:11434/v1");
    }
}
