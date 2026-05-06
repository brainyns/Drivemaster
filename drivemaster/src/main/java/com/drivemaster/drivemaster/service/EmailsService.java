package com.drivemaster.drivemaster.service;

public interface EmailsService {

    void enviarEmail(String destinatario, String asunto, String contenido);

}
