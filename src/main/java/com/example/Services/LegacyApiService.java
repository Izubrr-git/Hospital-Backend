package com.example.Services;

import com.example.DTO.LegacyClient;
import com.example.DTO.LegacyNote;
import com.example.DTO.NotesRequest;
import com.example.LegacyApiConfig;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.apache.hc.client5.http.config.RequestConfig;
import org.apache.hc.client5.http.impl.classic.CloseableHttpClient;
import org.apache.hc.client5.http.impl.classic.HttpClientBuilder;
import org.apache.hc.core5.util.Timeout;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.http.client.HttpComponentsClientHttpRequestFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.List;

@Service
@Slf4j
public class LegacyApiService {
    private final RestTemplate restTemplate;
    private final LegacyApiConfig config;
    private final ObjectMapper objectMapper;

    public LegacyApiService(LegacyApiConfig config) {
        this.config = config;
        this.objectMapper = new ObjectMapper();
        this.restTemplate = createRestTemplate();
    }

    private RestTemplate createRestTemplate() {
        RequestConfig requestConfig = RequestConfig.custom()
                .setConnectTimeout(Timeout.ofMilliseconds(config.getConnectTimeout()))
                .setResponseTimeout(Timeout.ofMilliseconds(config.getReadTimeout()))
                .build();

        CloseableHttpClient client = HttpClientBuilder.create()
                .setDefaultRequestConfig(requestConfig)
                .build();

        HttpComponentsClientHttpRequestFactory factory = new HttpComponentsClientHttpRequestFactory(client);
        return new RestTemplate(factory);
    }

    public List<LegacyClient> getAllClients() throws Exception {
        try {
            String url = config.getBaseUrl() + "/clients";
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);

            HttpEntity<String> entity = new HttpEntity<>("{}", headers);
            ResponseEntity<String> response = restTemplate.postForEntity(url, entity, String.class);

            return objectMapper.readValue(response.getBody(),
                    new TypeReference<List<LegacyClient>>() {});
        } catch (Exception e) {
            log.error("Ошибка получения клиентов из старой системы", e);
            throw e;
        }
    }

    public List<LegacyNote> getClientNotes(String agency, String clientGuid,
                                           LocalDate dateFrom, LocalDate dateTo) throws Exception {
        try {
            String url = config.getBaseUrl() + "/notes";
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);

            NotesRequest request = new NotesRequest(
                    agency,
                    dateFrom.format(DateTimeFormatter.ofPattern("yyyy-MM-dd")),
                    dateTo.format(DateTimeFormatter.ofPattern("yyyy-MM-dd")),
                    clientGuid
            );

            HttpEntity<NotesRequest> entity = new HttpEntity<>(request, headers);
            ResponseEntity<String> response = restTemplate.postForEntity(url, entity, String.class);

            return objectMapper.readValue(response.getBody(),
                    new TypeReference<List<LegacyNote>>() {});
        } catch (Exception e) {
            log.error("Ошибка получения заметок для клиента {}", clientGuid, e);
            throw e;
        }
    }
}
