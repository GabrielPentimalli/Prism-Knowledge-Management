package it.uniroma3.sii.service.chat;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.fasterxml.jackson.databind.ObjectMapper;

class QueryRewriteServiceTest {

    private LlmClient llm;
    private QueryRewriteService service;

    @BeforeEach
    void setUp() {
        llm = mock(LlmClient.class);
        service = new QueryRewriteService(llm, new ObjectMapper());
    }

    @Test
    void withoutHistory_returnsOriginalWithoutCallingLlm() {
        String out = service.rewrite("", "approfondisci");

        assertThat(out).isEqualTo("approfondisci");
        verify(llm, never()).chatStructured(anyString(), anyString(), anyString(), anyMap());
    }

    @Test
    void validRewrite_returnsStandaloneQuestion() {
        when(llm.chatStructured(anyString(), anyString(), anyString(), anyMap()))
                .thenReturn("{\"question\":\"Puoi approfondire il documento sul disgusto facciale?\"}");

        String out = service.rewrite("Utente: di cosa parla?\nAssistente: del disgusto facciale", "approfondisci");

        assertThat(out).isEqualTo("Puoi approfondire il documento sul disgusto facciale?");
    }

    @Test
    void unparsableOutput_fallsBackToOriginal() {
        when(llm.chatStructured(anyString(), anyString(), anyString(), anyMap()))
                .thenReturn("non è json");

        String out = service.rewrite("Utente: x\nAssistente: y", "approfondisci");

        assertThat(out).isEqualTo("approfondisci");
    }

    @Test
    void llmFailure_fallsBackToOriginal() {
        when(llm.chatStructured(anyString(), anyString(), anyString(), anyMap()))
                .thenThrow(new RuntimeException("ollama down"));

        String out = service.rewrite("Utente: x\nAssistente: y", "e il secondo?");

        assertThat(out).isEqualTo("e il secondo?");
    }
}
