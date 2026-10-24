package com.gestao.domain;

import jakarta.persistence.*;
import lombok.Data;
import java.time.LocalDate;
import java.util.UUID;

@Entity
@Data
@Table(name = "contratos")
public class Contrato {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne
    @JoinColumn(name = "aluno_id")
    private Usuario aluno;

    @ManyToOne
    @JoinColumn(name = "plano_id")
    private Plano plano;

    private Double valorAcordado; // Valor que o professor estipulou para este aluno
    private LocalDate dataInicio;
    private LocalDate dataVencimento;

    // O sistema de bloqueio vai olhar para cá!
    private Boolean contratoAceito;
    private Boolean ativo;
}