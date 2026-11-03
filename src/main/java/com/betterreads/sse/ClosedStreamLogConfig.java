package com.betterreads.sse;

import org.springframework.boot.tomcat.servlet.TomcatServletWebServerFactory;
import org.springframework.boot.web.server.WebServerFactoryCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
class ClosedStreamLogConfig {

    @Bean
    WebServerFactoryCustomizer<TomcatServletWebServerFactory> closedStreamCustomizer() {
        return factory -> factory.addContextValves(new ClosedStreamStatusValve());
    }
}
