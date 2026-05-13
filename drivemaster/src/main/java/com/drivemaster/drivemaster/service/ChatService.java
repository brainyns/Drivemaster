package com.drivemaster.drivemaster.service;

import com.drivemaster.drivemaster.dto.ChatRequest;
import com.drivemaster.drivemaster.dto.ChatResponse;

public interface ChatService {
    ChatResponse procesarMensaje(ChatRequest request);
}