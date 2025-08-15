package com.example.JPA_Entities;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "company_user")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class CompanyUser {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(unique = true, nullable = false)
    private String login;

    public CompanyUser(String login) {
        this.login = login;
    }
}
