package com.example.DTO;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.Data;

@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class LegacyNote {
    private String comments;
    private String guid;
    private String modifiedDateTime;
    private String clientGuid;
    private String datetime;
    private String loggedUser;
    private String createdDateTime;
}