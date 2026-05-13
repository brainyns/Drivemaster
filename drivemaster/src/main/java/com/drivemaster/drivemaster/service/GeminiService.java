package com.drivemaster.drivemaster.service;

import com.drivemaster.drivemaster.dto.AiIntentResponse;
import com.drivemaster.drivemaster.dto.ChatRequest;
import com.drivemaster.drivemaster.dto.ChatResponse;

public interface GeminiService {
    ChatResponse processChatRequest(ChatRequest request);
    AiIntentResponse interpretIntent(String mensaje);
}