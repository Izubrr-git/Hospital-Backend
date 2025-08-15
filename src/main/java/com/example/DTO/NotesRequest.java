package com.example.DTO;

import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
public class NotesRequest {
    private String agency;
    private String dateFrom;
    private String dateTo;
    private String clientGuid;
}
