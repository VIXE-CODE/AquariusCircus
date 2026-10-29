package com.gestao.domain;

import jakarta.persistence.*;
import lombok.Data;
import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Data
@Table(name = "checkins", uniqueConstraints = @UniqueConstraint(name = "uk_checkin_aluno_aula", columnNames = {"aluno_id", "aula_id"}))
public class Checkin {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne
    @JoinColumn(name = "aluno_id", nullable = false)
    private Usuario aluno;

    @ManyToOne
    @JoinColumn(name = "aula_id", nullable = false)
    private Aula aula;

    private LocalDateTime dataHoraAgendamento;

    // Estados: CONFIRMADO, PRESENTE, FALTA, CANCELADO
    @Column(nullable = false)
    private String status;
}