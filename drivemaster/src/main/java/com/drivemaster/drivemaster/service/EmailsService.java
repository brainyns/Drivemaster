package com.drivemaster.drivemaster.service;

public interface EmailsService {

    void enviarEmail(String destinatario, String asunto, String contenido);

    void enviarEmailConAdjunto(String destinatario, String asunto, String html,
                                byte[] adjunto, String nombreAdjunto);
}