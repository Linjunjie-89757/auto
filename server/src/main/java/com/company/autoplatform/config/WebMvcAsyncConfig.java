package com.company.autoplatform.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.AsyncSupportConfigurer;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
public class WebMvcAsyncConfig implements WebMvcConfigurer {

    private final long asyncRequestTimeoutMs;

    public WebMvcAsyncConfig(@Value("${app.web.async-request-timeout-ms:1800000}") long asyncRequestTimeoutMs) {
        this.asyncRequestTimeoutMs = Math.max(60000, asyncRequestTimeoutMs);
    }

    @Override
    public void configureAsyncSupport(AsyncSupportConfigurer configurer) {
        configurer.setDefaultTimeout(asyncRequestTimeoutMs);
    }
}
