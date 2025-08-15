package com.example;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Configuration
@ConfigurationProperties(prefix = "legacy.api")
@Data
public class LegacyApiConfig {
    private String baseUrl = "http://localhost:8080";
    private int connectTimeout = 10000;
    private int readTimeout = 30000;
}
