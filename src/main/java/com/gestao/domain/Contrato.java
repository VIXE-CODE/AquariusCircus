package com.gestao.domain;

import jakarta.persistence.*;
import lombok.Data;
import java.math.BigDecimal;
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

    @ManyToOne
    @JoinColumn(name = "professor_id")
    private Usuario professor;

    @Column(precision = 10, scale = 2)
    private BigDecimal valorAcordado;
    // Condições copiadas na contratação, independentes de futuras edições do plano.
    private Integer aulasContratadas;
    private Integer duracaoMeses;
    private LocalDate dataInicio;
    private LocalDate dataVencimento;

    // O sistema de bloqueio vai olhar para cá!
    private Boolean contratoAceito;
    private Boolean ativo;
}
