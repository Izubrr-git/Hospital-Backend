package com.example.service;

import com.example.config.LegacyApiConfig;
import com.example.dto.LegacyClient;
import com.example.dto.LegacyNote;
import com.example.dto.NotesRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.HttpServerErrorException;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestTemplate;

import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class LegacyApiServiceTest {

    @Mock
    private RestTemplate restTemplate;

    private LegacyApiService legacyApiService;
    private LegacyApiConfig config;

    @BeforeEach
    void setUp() {
        config = new LegacyApiConfig();
        config.setBaseUrl("http://localhost:8080");
        config.setConnectTimeout(5000);
        config.setReadTimeout(10000);

        legacyApiService = new LegacyApiService(config);

        // Inject mocked RestTemplate using reflection
        ReflectionTestUtils.setField(legacyApiService, "restTemplate", restTemplate);
    }

    @Test
    void shouldGetAllClientsSuccessfully() throws Exception {
        // Given
        String responseJson = """
            [
                {
                    "agency": "vhh4",
                    "guid": "01588E84-D45A-EB98-F47F-716073A4F1EF",
                    "firstName": "John",
                    "lastName": "Doe",
                    "status": "ACTIVE",
                    "dob": "10-15-1999",
                    "createdDateTime": "2021-11-15 11:51:59"
                }
            ]
            """;

        ResponseEntity<String> responseEntity = new ResponseEntity<>(responseJson, HttpStatus.OK);
        when(restTemplate.postForEntity(anyString(), any(HttpEntity.class), eq(String.class)))
                .thenReturn(responseEntity);

        // When
        List<LegacyClient> clients = legacyApiService.getAllClients();

        // Then
        assertThat(clients).hasSize(1);
        assertThat(clients.get(0).getGuid()).isEqualTo("01588E84-D45A-EB98-F47F-716073A4F1EF");
        assertThat(clients.get(0).getAgency()).isEqualTo("vhh4");
        assertThat(clients.get(0).getFirstName()).isEqualTo("John");
        assertThat(clients.get(0).getLastName()).isEqualTo("Doe");
        assertThat(clients.get(0).getStatus()).isEqualTo("ACTIVE");

        verify(restTemplate).postForEntity(
                eq("http://localhost:8080/clients"),
                any(HttpEntity.class),
                eq(String.class)
        );
    }

    @Test
    void shouldGetClientNotesSuccessfully() throws Exception {
        // Given
        String responseJson = """
            [
                {
                    "comments": "Test note comment",
                    "guid": "20CBCEDA-3764-7F20-0BB6-4D6DD46BA9F8",
                    "modifiedDateTime": "2021-11-15 11:51:59",
                    "clientGuid": "C5DCAA49-ADE5-E65C-B776-3F6D7B5F2055",
                    "datetime": "2021-09-16 12:02:26 CDT",
                    "loggedUser": "p.vasya",
                    "createdDateTime": "2021-11-15 11:51:59"
                }
            ]
            """;

        ResponseEntity<String> responseEntity = new ResponseEntity<>(responseJson, HttpStatus.OK);
        when(restTemplate.postForEntity(anyString(), any(HttpEntity.class), eq(String.class)))
                .thenReturn(responseEntity);

        // When
        List<LegacyNote> notes = legacyApiService.getClientNotes(
                "vhh4",
                "test-guid",
                LocalDate.of(2021, 1, 1),
                LocalDate.of(2021, 12, 31)
        );

        // Then
        assertThat(notes).hasSize(1);
        LegacyNote note = notes.get(0);
        assertThat(note.getGuid()).isEqualTo("20CBCEDA-3764-7F20-0BB6-4D6DD46BA9F8");
        assertThat(note.getComments()).isEqualTo("Test note comment");
        assertThat(note.getLoggedUser()).isEqualTo("p.vasya");
        assertThat(note.getClientGuid()).isEqualTo("C5DCAA49-ADE5-E65C-B776-3F6D7B5F2055");

        // Verify correct request was made
        ArgumentCaptor<HttpEntity> entityCaptor = ArgumentCaptor.forClass(HttpEntity.class);
        verify(restTemplate).postForEntity(
                eq("http://localhost:8080/notes"),
                entityCaptor.capture(),
                eq(String.class)
        );

        // Check request body
        HttpEntity<?> capturedEntity = entityCaptor.getValue();
        assertThat(capturedEntity.getBody()).isInstanceOf(NotesRequest.class);
        NotesRequest request = (NotesRequest) capturedEntity.getBody();
        assertThat(request.getAgency()).isEqualTo("vhh4");
        assertThat(request.getClientGuid()).isEqualTo("test-guid");
        assertThat(request.getDateFrom()).isEqualTo("2021-01-01");
        assertThat(request.getDateTo()).isEqualTo("2021-12-31");
    }

    @Test
    void shouldThrowExceptionWhenApiUnavailable() {
        // Given
        when(restTemplate.postForEntity(anyString(), any(HttpEntity.class), eq(String.class)))
                .thenThrow(new ResourceAccessException("Connection timeout"));

        // When & Then
        assertThatThrownBy(() -> legacyApiService.getAllClients())
                .isInstanceOf(Exception.class)
                .hasMessageContaining("Ошибка получения клиентов из старой системы");
    }

    @Test
    void shouldHandleInvalidJsonResponse() {
        // Given
        String invalidJson = "{ invalid json }";
        ResponseEntity<String> responseEntity = new ResponseEntity<>(invalidJson, HttpStatus.OK);
        when(restTemplate.postForEntity(anyString(), any(HttpEntity.class), eq(String.class)))
                .thenReturn(responseEntity);

        // When & Then
        assertThatThrownBy(() -> legacyApiService.getAllClients())
                .isInstanceOf(Exception.class);
    }

    @Test
    void shouldHandleEmptyResponse() throws Exception {
        // Given
        String emptyResponse = "[]";
        ResponseEntity<String> responseEntity = new ResponseEntity<>(emptyResponse, HttpStatus.OK);
        when(restTemplate.postForEntity(anyString(), any(HttpEntity.class), eq(String.class)))
                .thenReturn(responseEntity);

        // When
        List<LegacyClient> clients = legacyApiService.getAllClients();

        // Then
        assertThat(clients).isEmpty();
    }

    @Test
    void shouldHandle404Response() {
        // Given
        when(restTemplate.postForEntity(anyString(), any(HttpEntity.class), eq(String.class)))
                .thenThrow(new HttpClientErrorException(HttpStatus.NOT_FOUND));

        // When & Then
        assertThatThrownBy(() -> legacyApiService.getAllClients())
                .isInstanceOf(Exception.class);
    }

    @Test
    void shouldHandle500Response() {
        // Given
        when(restTemplate.postForEntity(anyString(), any(HttpEntity.class), eq(String.class)))
                .thenThrow(new HttpServerErrorException(HttpStatus.INTERNAL_SERVER_ERROR));

        // When & Then
        assertThatThrownBy(() -> legacyApiService.getAllClients())
                .isInstanceOf(Exception.class);
    }

    @Test
    void shouldHandleNullResponse() {
        // Given
        when(restTemplate.postForEntity(anyString(), any(HttpEntity.class), eq(String.class)))
                .thenReturn(new ResponseEntity<>(null, HttpStatus.OK));

        // When & Then
        assertThatThrownBy(() -> legacyApiService.getAllClients())
                .isInstanceOf(Exception.class);
    }

    @Test
    void shouldValidateNotesRequestParameters() throws Exception {
        // Given
        String responseJson = "[]";
        ResponseEntity<String> responseEntity = new ResponseEntity<>(responseJson, HttpStatus.OK);
        when(restTemplate.postForEntity(anyString(), any(HttpEntity.class), eq(String.class)))
                .thenReturn(responseEntity);

        LocalDate dateFrom = LocalDate.of(2023, 1, 1);
        LocalDate dateTo = LocalDate.of(2023, 12, 31);

        // When
        legacyApiService.getClientNotes("test-agency", "test-client-guid", dateFrom, dateTo);

        // Then
        ArgumentCaptor<HttpEntity> entityCaptor = ArgumentCaptor.forClass(HttpEntity.class);
        verify(restTemplate).postForEntity(anyString(), entityCaptor.capture(), eq(String.class));

        HttpEntity<?> entity = entityCaptor.getValue();
        NotesRequest request = (NotesRequest) entity.getBody();

        assertThat(request.getAgency()).isEqualTo("test-agency");
        assertThat(request.getClientGuid()).isEqualTo("test-client-guid");
        assertThat(request.getDateFrom()).isEqualTo("2023-01-01");
        assertThat(request.getDateTo()).isEqualTo("2023-12-31");
    }
}
