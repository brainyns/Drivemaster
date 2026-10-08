package com.drivemaster.drivemaster.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestTemplate;

@Configuration
public class RestTemplateConfig {

    /** Brevo: si no responde, el envío falla pronto en vez de dejar colgado el pago o el aviso que lo disparó. */
    public static final int BREVO_CONNECT_TIMEOUT_MS = 5_000;
    public static final int BREVO_READ_TIMEOUT_MS = 15_000;

    @Bean
    @Primary
    public RestTemplate restTemplate() {
        return new RestTemplate();
    }

    @Bean("brevoRestTemplate")
    public RestTemplate brevoRestTemplate() {
        SimpleClientHttpRequestFactory fabrica = new SimpleClientHttpRequestFactory();
        fabrica.setConnectTimeout(BREVO_CONNECT_TIMEOUT_MS);
        fabrica.setReadTimeout(BREVO_READ_TIMEOUT_MS);
        return new RestTemplate(fabrica);
    }
}
