package com.gestao.domain;

import jakarta.persistence.*;
import lombok.Data;
import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Data
@Table(name = "checkins")
public class Checkin {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne
    @JoinColumn(name = "aluno_id")
    private Usuario aluno;

    @ManyToOne
    @JoinColumn(name = "aula_id")
    private Aula aula;

    private LocalDateTime dataHoraAgendamento;

    // Status possíveis: AGENDADO, PRESENTE, FALTA, CANCELADO
    private String status;
}