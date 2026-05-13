package com.drivemaster.drivemaster.controller;

import com.drivemaster.drivemaster.dto.ChatRequest;
import com.drivemaster.drivemaster.dto.ChatResponse;
import com.drivemaster.drivemaster.service.GeminiService;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/chat")
@AllArgsConstructor
@Slf4j
@CrossOrigin(origins = "*")
public class ChatController {

    private final GeminiService geminiService;

    @PostMapping
    public ResponseEntity<ChatResponse> chat(@RequestBody ChatRequest request) {
        log.info("Recibida solicitud de chat: {}", request.getMensaje());
        ChatResponse response = geminiService.processChatRequest(request);
        return ResponseEntity.ok(response);
    }
}