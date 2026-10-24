package com.gestao.domain;

import jakarta.persistence.*;
import lombok.Data;
import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "aulas")
@Data
public class Aula {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    private String titulo;

    // Ex: 25/10/2026 às 19:00
    private LocalDateTime dataHora;

    private Integer limiteAlunos; // Ex: Máximo de 15 pessoas

    @ManyToOne
    @JoinColumn(name = "professor_id")
    private Usuario professor;
}