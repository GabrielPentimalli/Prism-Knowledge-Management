package it.uniroma3.sii.controller;

import java.util.List;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import it.uniroma3.sii.dto.chat.ChatMessage;
import it.uniroma3.sii.dto.chat.ChatRequest;
import it.uniroma3.sii.dto.chat.ChatResponse;
import it.uniroma3.sii.dto.chat.ChatSessionSummary;
import it.uniroma3.sii.model.ChatScopeType;
import it.uniroma3.sii.service.chat.ChatService;

@RestController
@RequestMapping("/api/chat")
public class ChatController {

    private final ChatService chatService;

    public ChatController(ChatService chatService) {
        this.chatService = chatService;
    }

    @PostMapping
    public ResponseEntity<ChatResponse> chat(@RequestBody ChatRequest request) {
        return ResponseEntity.ok(chatService.processMessage(request));
    }

    @GetMapping("/sessions")
    public ResponseEntity<List<ChatSessionSummary>> sessions(
            @RequestParam ChatScopeType scopeType,
            @RequestParam String scopeId) {
        return ResponseEntity.ok(chatService.listSessions(scopeType, scopeId));
    }

    @GetMapping("/{sessionId}/history")
    public ResponseEntity<List<ChatMessage>> history(@PathVariable String sessionId) {
        return ResponseEntity.ok(chatService.history(sessionId));
    }
}
