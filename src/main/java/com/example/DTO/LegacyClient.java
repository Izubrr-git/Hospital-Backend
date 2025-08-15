package com.example.DTO;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.Data;

@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class LegacyClient {
    private String agency;
    private String guid;
    private String firstName;
    private String lastName;
    private String status;
    private String dob;
    private String createdDateTime;
}
