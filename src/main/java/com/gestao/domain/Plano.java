package com.gestao.domain;

import jakarta.persistence.*;
import lombok.Data;
import java.util.UUID;

@Entity
@Data
@Table(name = "planos")
public class Plano {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    private String nome; // Ex: "Mensal 3x", "Trimestral Livre"
    private Integer limiteAulasPorSemana;
    private Double valorPadrao;
}