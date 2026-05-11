package it.uniroma3.sii.service.chat;

import java.io.IOException;

import static org.assertj.core.api.Assertions.assertThat;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import okhttp3.mockwebserver.RecordedRequest;

class LlmClientTest {

    private MockWebServer mockWebServer;
    private LlmClient llmClient;

    @BeforeEach
    void setUp() throws IOException {
        mockWebServer = new MockWebServer();
        mockWebServer.start();
        llmClient = new LlmClient(mockWebServer.url("/v1").toString(), "test-model");
    }

    @AfterEach
    void tearDown() throws IOException {
        mockWebServer.shutdown();
    }

    // Verifica che il contenuto venga estratto correttamente da choices[0].message.content.
    @Test
    void chat_validResponse_extractsContentFromChoices() throws InterruptedException {
        mockWebServer.enqueue(new MockResponse()
                .setBody("""
                        {
                          "choices": [
                            {
                              "message": {
                                "content": "risposta del modello"
                              }
                            }
                          ]
                        }
                        """)
                .addHeader("Content-Type", "application/json"));

        String result = llmClient.chat("system prompt", "user message");

        assertThat(result).isEqualTo("risposta del modello");
    }

    // Verifica che la chiamata HTTP sia una POST verso /chat/completions.
    @Test
    void chat_sendsPostToChatCompletionsEndpoint() throws InterruptedException {
        mockWebServer.enqueue(new MockResponse()
                .setBody("{\"choices\":[{\"message\":{\"content\":\"ok\"}}]}")
                .addHeader("Content-Type", "application/json"));

        llmClient.chat("system", "user");

        RecordedRequest request = mockWebServer.takeRequest();
        assertThat(request.getMethod()).isEqualTo("POST");
        assertThat(request.getPath()).endsWith("/chat/completions");
    }

    // Verifica che nel body siano presenti modello e messaggi system/user.
    @Test
    void chat_requestBodyContainsModelAndMessages() throws InterruptedException {
        mockWebServer.enqueue(new MockResponse()
                .setBody("{\"choices\":[{\"message\":{\"content\":\"ok\"}}]}")
                .addHeader("Content-Type", "application/json"));

        llmClient.chat("istruzioni sistema", "domanda utente");

        RecordedRequest request = mockWebServer.takeRequest();
        String body = request.getBody().readUtf8();

        assertThat(body).contains("\"model\"");
        assertThat(body).contains("test-model");
        assertThat(body).contains("\"messages\"");
        assertThat(body).contains("\"system\"");
        assertThat(body).contains("istruzioni sistema");
        assertThat(body).contains("\"user\"");
        assertThat(body).contains("domanda utente");
    }

    // Verifica che, con piu' scelte, venga usata la prima risposta disponibile.
    @Test
    void chat_jsonResponseWithMultipleChoices_returnsFirstChoice() throws InterruptedException {
        mockWebServer.enqueue(new MockResponse()
                .setBody("""
                        {
                          "choices": [
                            {"message": {"content": "prima risposta"}},
                            {"message": {"content": "seconda risposta"}}
                          ]
                        }
                        """)
                .addHeader("Content-Type", "application/json"));

        String result = llmClient.chat("system", "user");

        assertThat(result).isEqualTo("prima risposta");
    }

    // Verifica che il contenuto JSON venga restituito integralmente come testo.
    @Test
    void chat_jsonResponse_extractsContentField() throws InterruptedException {
        mockWebServer.enqueue(new MockResponse()
                .setBody("""
                        {"choices":[{"message":{"content":"{\\\"intent\\\":\\\"SEARCH\\\",\\\"keywords\\\":[\\\"cancer\\\"]}"}}]}
                        """)
                .addHeader("Content-Type", "application/json"));

        String result = llmClient.chat("system", "articoli sul cancro");

        assertThat(result).contains("SEARCH");
        assertThat(result).contains("cancer");
    }

      // Verifica che una risposta senza choices venga gestita restituendo stringa vuota.
      @Test
      void chat_missingChoices_returnsEmptyString() {
        mockWebServer.enqueue(new MockResponse()
            .setBody("{}")
            .addHeader("Content-Type", "application/json"));

        String result = llmClient.chat("system", "user");

        assertThat(result).isEqualTo("");
      }
}
