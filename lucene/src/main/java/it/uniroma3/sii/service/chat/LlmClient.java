package it.uniroma3.sii.service.chat;

import com.fasterxml.jackson.databind.JsonNode;
import java.net.URI;
import java.util.Locale;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;

import java.util.List;
import java.util.Map;

/**
 * Client HTTP verso Ollama (API compatibile OpenAI).
 * Si riconfigura a caldo quando le impostazioni cambiano.
 */
@Component
public class LlmClient {

    private volatile WebClient webClient;
    private volatile String model;
    private volatile String baseUrl;

    public LlmClient(
            @Value("${llm.base-url}") String baseUrl,
            @Value("${llm.model}") String model) {
        configure(baseUrl, model);
    }

    /**
     * Riconfigura il client con nuovi parametri (chiamato dopo update settings o all'app start).
     * Valida il vincolo local-first: solo localhost è ammesso.
     */
    public synchronized void configure(String newBaseUrl, String newModel) {
        if (newBaseUrl == null || newBaseUrl.isBlank()) {
            throw new IllegalArgumentException("llm.base-url non può essere vuoto");
        }
        if (newModel == null || newModel.isBlank()) {
            throw new IllegalArgumentException("llm.model non può essere vuoto");
        }
        ensureLocalhost(newBaseUrl);
        this.baseUrl = newBaseUrl;
        this.model = newModel;
        this.webClient = WebClient.builder()
                .baseUrl(newBaseUrl)
                .codecs(c -> c.defaultCodecs().maxInMemorySize(2 * 1024 * 1024))
                .build();
    }

    public String chat(String systemPrompt, String userMessage) {
        return chat(systemPrompt, userMessage, false);
    }

    /**
     * Variante con possibilità di forzare l'output in JSON.
     * I modelli locali piccoli rispettano lo schema in modo molto più affidabile
     * quando si imposta response_format=json_object (supportato dall'API
     * OpenAI-compatible di Ollama).
     */
    public String chat(String systemPrompt, String userMessage, boolean jsonMode) {
        Map<String, Object> messages = Map.of(
                "model", model,
                "messages", List.of(
                        Map.of("role", "system", "content", systemPrompt),
                        Map.of("role", "user",   "content", userMessage)
                )
        );
        Map<String, Object> body = jsonMode
                ? withJsonFormat(messages)
                : messages;

        return webClient.post()
                .uri("/chat/completions")
                .bodyValue(body)
                .retrieve()
                .bodyToMono(JsonNode.class)
                .map(resp -> resp.at("/choices/0/message/content").asText())
                .block();
    }

    private Map<String, Object> withJsonFormat(Map<String, Object> base) {
        java.util.Map<String, Object> body = new java.util.HashMap<>(base);
        body.put("response_format", Map.of("type", "json_object"));
        body.put("temperature", 0);
        return body;
    }

    /**
     * Chiamata con structured output vincolato a uno schema JSON (strict).
     * I modelli locali piccoli, vincolati allo schema, smettono di inventare
     * nomi di campo arbitrari e rispettano la forma richiesta.
     *
     * @param schemaName nome logico dello schema
     * @param schema     schema JSON (mappa con type/properties/required/...)
     */
    public String chatStructured(String systemPrompt, String userMessage,
                                 String schemaName, Map<String, Object> schema) {
        java.util.Map<String, Object> body = new java.util.HashMap<>();
        body.put("model", model);
        body.put("temperature", 0);
        body.put("messages", List.of(
                Map.of("role", "system", "content", systemPrompt),
                Map.of("role", "user",   "content", userMessage)
        ));
        body.put("response_format", Map.of(
                "type", "json_schema",
                "json_schema", Map.of(
                        "name", schemaName,
                        "strict", true,
                        "schema", schema
                )
        ));

        return webClient.post()
                .uri("/chat/completions")
                .bodyValue(body)
                .retrieve()
                .bodyToMono(JsonNode.class)
                .map(resp -> resp.at("/choices/0/message/content").asText())
                .block();
    }

    public boolean isModelAvailable() {
        try {
            JsonNode response = webClient.get()
                    .uri("/models")
                    .retrieve()
                    .bodyToMono(JsonNode.class)
                    .block();
            if (response == null) {
                return false;
            }
            JsonNode data = response.get("data");
            if (data == null || !data.isArray()) {
                return false;
            }
            for (JsonNode modelNode : data) {
                String id = modelNode.path("id").asText("");
                if (id.equalsIgnoreCase(model)) {
                    return true;
                }
            }
            return false;
        } catch (Exception e) {
            return false;
        }
    }

    public String getModel() {
        return model;
    }

    public String getBaseUrl() {
        return baseUrl;
    }

    private void ensureLocalhost(String baseUrl) {
        URI uri = URI.create(baseUrl);
        String host = uri.getHost() == null ? "" : uri.getHost().toLowerCase(Locale.ROOT);
        if (!(host.equals("localhost") || host.equals("127.0.0.1") || host.equals("::1"))) {
            throw new IllegalStateException("Per privacy local-first, llm.base-url deve puntare a localhost.");
        }
    }
}
