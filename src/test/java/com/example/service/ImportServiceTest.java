package com.example.service;

import com.example.config.ImportConfigProperties;
import com.example.dto.LegacyClient;
import com.example.dto.LegacyNote;
import com.example.entity.CompanyUser;
import com.example.entity.PatientNote;
import com.example.entity.PatientProfile;
import com.example.model.ImportStatistics;
import com.example.monitoring.ImportMetrics;
import com.example.repository.CompanyUserRepository;
import com.example.repository.PatientNoteRepository;
import com.example.repository.PatientProfileRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ImportServiceTest {

    @Mock
    private LegacyApiService legacyApiService;

    @Mock
    private PatientProfileRepository patientRepository;

    @Mock
    private CompanyUserRepository userRepository;

    @Mock
    private PatientNoteRepository noteRepository;

    @Mock
    private ImportConfigProperties config;

    @Mock
    private ImportMetrics metrics;

    @InjectMocks
    private EnhancedImportService importService;

    @BeforeEach
    void setUp() {
        when(config.getDaysToImport()).thenReturn(30);
        when(config.getPatientBatchSize()).thenReturn(100);
    }

    @Test
    void shouldCreateNewNoteWhenNotExists() throws Exception {
        PatientProfile patient = createTestPatient();
        LegacyClient legacyClient = createTestLegacyClient();
        LegacyNote legacyNote = createTestLegacyNote();
        CompanyUser user = createTestUser();

        when(patientRepository.findActivePatients()).thenReturn(List.of(patient));
        when(legacyApiService.getAllClients()).thenReturn(List.of(legacyClient));
        when(legacyApiService.getClientNotes(any(), any(), any(), any()))
                .thenReturn(List.of(legacyNote));
        when(userRepository.findByLogin("testuser")).thenReturn(Optional.of(user));
        when(noteRepository.findByLegacyNoteGuid(legacyNote.getGuid()))
                .thenReturn(Optional.empty());

        ImportStatistics result = importService.performImport();

        assertThat(result.getCreatedCount()).isEqualTo(1);
        assertThat(result.getErrorCount()).isEqualTo(0);
        assertThat(result.isHasCriticalError()).isFalse();

        verify(noteRepository).save(any(PatientNote.class));
        verify(metrics).recordImportStarted();
        verify(metrics).recordImportCompleted(any(Duration.class), eq(true));
    }

    @Test
    void shouldUpdateExistingNoteWhenLegacyIsNewer() throws Exception {
        // Given
        PatientProfile patient = createTestPatient();
        LegacyClient legacyClient = createTestLegacyClient();
        LegacyNote legacyNote = createTestLegacyNote();
        CompanyUser user = createTestUser();

        PatientNote existingNote = new PatientNote();
        existingNote.setLegacyNoteGuid(legacyNote.getGuid());
        existingNote.setLastModifiedDateTime(LocalDateTime.now().minusHours(1));
        existingNote.setNote("Старый текст");

        when(patientRepository.findActivePatients()).thenReturn(List.of(patient));
        when(legacyApiService.getAllClients()).thenReturn(List.of(legacyClient));
        when(legacyApiService.getClientNotes(any(), any(), any(), any()))
                .thenReturn(List.of(legacyNote));
        when(userRepository.findByLogin("testuser")).thenReturn(Optional.of(user));
        when(noteRepository.findByLegacyNoteGuid(legacyNote.getGuid()))
                .thenReturn(Optional.of(existingNote));

        ImportStatistics result = importService.performImport();

        assertThat(result.getUpdatedCount()).isEqualTo(1);
        assertThat(result.getCreatedCount()).isEqualTo(0);

        verify(noteRepository).save(existingNote);
        assertThat(existingNote.getNote()).isEqualTo("Test comment");
        assertThat(existingNote.getLastModifiedByUser()).isEqualTo(user);
    }

    @Test
    void shouldSkipNoteWhenLocalIsNewer() throws Exception {
        PatientProfile patient = createTestPatient();
        LegacyClient legacyClient = createTestLegacyClient();
        LegacyNote legacyNote = createTestLegacyNote();
        CompanyUser user = createTestUser();

        PatientNote existingNote = new PatientNote();
        existingNote.setLegacyNoteGuid(legacyNote.getGuid());
        existingNote.setLastModifiedDateTime(LocalDateTime.now().plusHours(1)); // Новее чем в legacy

        when(patientRepository.findActivePatients()).thenReturn(List.of(patient));
        when(legacyApiService.getAllClients()).thenReturn(List.of(legacyClient));
        when(legacyApiService.getClientNotes(any(), any(), any(), any()))
                .thenReturn(List.of(legacyNote));
        when(userRepository.findByLogin("testuser")).thenReturn(Optional.of(user));
        when(noteRepository.findByLegacyNoteGuid(legacyNote.getGuid()))
                .thenReturn(Optional.of(existingNote));

        ImportStatistics result = importService.performImport();

        assertThat(result.getSkippedCount()).isEqualTo(1);
        assertThat(result.getUpdatedCount()).isEqualTo(0);
        assertThat(result.getCreatedCount()).isEqualTo(0);

        verify(noteRepository, never()).save(any());
    }

    @Test
    void shouldCreateUserWhenNotExists() throws Exception {
        PatientProfile patient = createTestPatient();
        LegacyClient legacyClient = createTestLegacyClient();
        LegacyNote legacyNote = createTestLegacyNote();
        CompanyUser newUser = createTestUser();

        when(patientRepository.findActivePatients()).thenReturn(List.of(patient));
        when(legacyApiService.getAllClients()).thenReturn(List.of(legacyClient));
        when(legacyApiService.getClientNotes(any(), any(), any(), any()))
                .thenReturn(List.of(legacyNote));
        when(userRepository.findByLogin("testuser")).thenReturn(Optional.empty());
        when(userRepository.save(any(CompanyUser.class))).thenReturn(newUser);
        when(noteRepository.findByLegacyNoteGuid(any())).thenReturn(Optional.empty());

        importService.performImport();

        verify(userRepository).save(argThat(user ->
                "testuser".equals(user.getLogin())));
    }

    @Test
    void shouldHandleEmptyActivePatients() throws Exception {
        when(patientRepository.findActivePatients()).thenReturn(Collections.emptyList());

        ImportStatistics result = importService.performImport();

        assertThat(result.getCreatedCount()).isEqualTo(0);
        assertThat(result.getUpdatedCount()).isEqualTo(0);
        assertThat(result.getSkippedCount()).isEqualTo(0);
        assertThat(result.getErrorCount()).isEqualTo(0);

        verify(legacyApiService, never()).getAllClients();
        verify(metrics).recordImportStarted();
    }

    @Test
    void shouldHandleLegacyApiError() throws Exception {
        when(patientRepository.findActivePatients()).thenReturn(List.of(createTestPatient()));
        when(legacyApiService.getAllClients()).thenThrow(new RuntimeException("API недоступен"));

        ImportStatistics result = importService.performImport();

        assertThat(result.isHasCriticalError()).isTrue();
        verify(metrics).recordImportError("critical_error");
    }

    @Test
    void shouldValidateNoteData() {
        PatientProfile patient = createTestPatient();
        LegacyNote invalidNote = new LegacyNote();
        invalidNote.setGuid(null);
        invalidNote.setComments("Valid comment");
        invalidNote.setLoggedUser("testuser");
        invalidNote.setCreatedDateTime("2023-01-01 10:00:00");
        invalidNote.setModifiedDateTime("2023-01-01 10:00:00");

        ImportStatistics stats = new ImportStatistics();

        importService.importSingleNote(patient, invalidNote, stats);

        assertThat(stats.getSkippedCount()).isEqualTo(1);
        verify(noteRepository, never()).save(any());
    }

    @Test
    void shouldHandleEmptyComments() {
        PatientProfile patient = createTestPatient();
        LegacyNote emptyNote = new LegacyNote();
        emptyNote.setGuid("valid-guid");
        emptyNote.setComments("");
        emptyNote.setLoggedUser("testuser");
        emptyNote.setCreatedDateTime("2023-01-01 10:00:00");
        emptyNote.setModifiedDateTime("2023-01-01 10:00:00");

        ImportStatistics stats = new ImportStatistics();

        importService.importSingleNote(patient, emptyNote, stats);

        assertThat(stats.getSkippedCount()).isEqualTo(1);
        verify(noteRepository, never()).save(any());
    }

    @Test
    void shouldHandleInvalidDates() {
        PatientProfile patient = createTestPatient();
        LegacyNote noteWithInvalidDate = createTestLegacyNote();
        noteWithInvalidDate.setCreatedDateTime("invalid-date");
        noteWithInvalidDate.setModifiedDateTime("another-invalid-date");

        CompanyUser user = createTestUser();
        when(userRepository.findByLogin("testuser")).thenReturn(Optional.of(user));
        when(noteRepository.findByLegacyNoteGuid(any())).thenReturn(Optional.empty());

        ImportStatistics stats = new ImportStatistics();

        importService.importSingleNote(patient, noteWithInvalidDate, stats);

        assertThat(stats.getCreatedCount()).isEqualTo(1);

        ArgumentCaptor<PatientNote> noteCaptor = ArgumentCaptor.forClass(PatientNote.class);
        verify(noteRepository).save(noteCaptor.capture());

        PatientNote savedNote = noteCaptor.getValue();
        assertThat(savedNote.getCreatedDateTime()).isNotNull();
        assertThat(savedNote.getLastModifiedDateTime()).isNotNull();
    }

    @Test
    void shouldGetImportStatistics() {
        when(noteRepository.count()).thenReturn(1000L);
        when(noteRepository.countImportedNotes()).thenReturn(800L);
        when(noteRepository.findTopByLegacyNoteGuidIsNotNullOrderByLastModifiedDateTimeDesc())
                .thenReturn(Optional.of(createTestPatientNote()));
        when(noteRepository.countImportedSince(any())).thenReturn(50L);

        ImportStatistics stats = importService.getImportStatistics();

        assertThat(stats).isNotNull();
        verify(noteRepository).count();
        verify(noteRepository).countImportedNotes();
    }

    @Test
    void shouldImportSpecificPatient() throws Exception {
        Long patientId = 1L;
        PatientProfile patient = createTestPatient();
        patient.setId(patientId);

        LegacyClient legacyClient = createTestLegacyClient();
        LegacyNote legacyNote = createTestLegacyNote();

        when(patientRepository.findById(patientId)).thenReturn(Optional.of(patient));
        when(legacyApiService.getAllClients()).thenReturn(List.of(legacyClient));
        when(legacyApiService.getClientNotes(any(), any(), any(), any()))
                .thenReturn(List.of(legacyNote));
        when(userRepository.findByLogin(any())).thenReturn(Optional.of(createTestUser()));
        when(noteRepository.findByLegacyNoteGuid(any())).thenReturn(Optional.empty());

        ImportStatistics result = importService.importSpecificPatient(patientId);

        assertThat(result.getCreatedCount()).isEqualTo(1);
        assertThat(result.isHasCriticalError()).isFalse();
    }

    @Test
    void shouldFailImportForNonExistentPatient() {
        Long nonExistentPatientId = 999L;
        when(patientRepository.findById(nonExistentPatientId)).thenReturn(Optional.empty());

        ImportStatistics result = importService.importSpecificPatient(nonExistentPatientId);

        assertThat(result.isHasCriticalError()).isTrue();
        assertThat(result.getErrorCount()).isEqualTo(1);
    }

    @Test
    void shouldFailImportForInactivePatient() {
        Long patientId = 1L;
        PatientProfile inactivePatient = createTestPatient();
        inactivePatient.setId(patientId);
        inactivePatient.setStatusId((short) 100);

        when(patientRepository.findById(patientId)).thenReturn(Optional.of(inactivePatient));

        ImportStatistics result = importService.importSpecificPatient(patientId);

        assertThat(result.isHasCriticalError()).isTrue();
        assertThat(result.getErrorCount()).isEqualTo(1);
    }

    private PatientProfile createTestPatient() {
        PatientProfile patient = new PatientProfile();
        patient.setId(1L);
        patient.setFirstName("Тест");
        patient.setLastName("Пациент");
        patient.setStatusId((short) 200);
        patient.setOldClientGuid("test-guid-1");
        return patient;
    }

    private LegacyClient createTestLegacyClient() {
        LegacyClient client = new LegacyClient();
        client.setGuid("test-guid-1");
        client.setAgency("test-agency");
        client.setFirstName("Тест");
        client.setLastName("Клиент");
        client.setStatus("ACTIVE");
        return client;
    }

    private LegacyNote createTestLegacyNote() {
        LegacyNote note = new LegacyNote();
        note.setGuid("note-guid-1");
        note.setComments("Test comment");
        note.setLoggedUser("testuser");
        note.setCreatedDateTime("2023-01-01 10:00:00");
        note.setModifiedDateTime("2023-01-01 10:00:00");
        note.setClientGuid("test-guid-1");
        return note;
    }

    private CompanyUser createTestUser() {
        return new CompanyUser(1L, "testuser");
    }

    private PatientNote createTestPatientNote() {
        PatientNote note = new PatientNote();
        note.setId(1L);
        note.setCreatedDateTime(LocalDateTime.now().minusDays(1));
        note.setLastModifiedDateTime(LocalDateTime.now());
        note.setNote("Test note");
        note.setLegacyNoteGuid("test-legacy-guid");
        return note;
    }
}
