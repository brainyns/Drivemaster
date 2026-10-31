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

    /** Wompi Payouts: un pago colgado no debe dejar la petición del ADMIN esperando sin fin. */
    public static final int PAYOUTS_CONNECT_TIMEOUT_MS = 5_000;
    public static final int PAYOUTS_READ_TIMEOUT_MS = 20_000;

    @Bean
    @Primary
    public RestTemplate restTemplate() {
        return new RestTemplate();
    }

    @Bean("wompiPayoutsRestTemplate")
    public RestTemplate wompiPayoutsRestTemplate() {
        SimpleClientHttpRequestFactory fabrica = new SimpleClientHttpRequestFactory();
        fabrica.setConnectTimeout(PAYOUTS_CONNECT_TIMEOUT_MS);
        fabrica.setReadTimeout(PAYOUTS_READ_TIMEOUT_MS);
        return new RestTemplate(fabrica);
    }

    @Bean("brevoRestTemplate")
    public RestTemplate brevoRestTemplate() {
        SimpleClientHttpRequestFactory fabrica = new SimpleClientHttpRequestFactory();
        fabrica.setConnectTimeout(BREVO_CONNECT_TIMEOUT_MS);
        fabrica.setReadTimeout(BREVO_READ_TIMEOUT_MS);
        return new RestTemplate(fabrica);
    }
}
