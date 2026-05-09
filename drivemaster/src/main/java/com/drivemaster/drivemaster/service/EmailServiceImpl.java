package com.drivemaster.drivemaster.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Service;

import jakarta.mail.MessagingException;
import jakarta.mail.internet.MimeMessage;
import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class EmailServiceImpl implements EmailsService {

    private final JavaMailSender mailSender;

    @Value("${spring.mail.username}")
    private String username;

    @Override
    public void enviarEmail(String destinatario, String asunto, String contenido) {
        try {
            MimeMessage mensaje = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(mensaje, true, "UTF-8");
            helper.setFrom(username);
            helper.setTo(destinatario);
            helper.setSubject(asunto);
            helper.setText(contenido, true);
            mailSender.send(mensaje);
        } catch (MessagingException e) {
            throw new RuntimeException("Error al enviar el correo: " + e.getMessage(), e);
        }
    }

    @Override
    public void enviarEmailConAdjunto(String destinatario, String asunto, String html,
                                       byte[] adjunto, String nombreAdjunto) {
        try {
            MimeMessage mensaje = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(mensaje, true, "UTF-8");
            helper.setFrom(username);
            helper.setTo(destinatario);
            helper.setSubject(asunto);
            helper.setText(html, true);
            helper.addAttachment(nombreAdjunto,
                    new ByteArrayResource(adjunto),
                    "application/pdf");
            mailSender.send(mensaje);
        } catch (MessagingException e) {
            throw new RuntimeException("Error al enviar el correo: " + e.getMessage(), e);
        }
    }
}