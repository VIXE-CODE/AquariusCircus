package com.gestao.dto;
import java.time.LocalDateTime;
import java.util.UUID;
public record AulaResponseDTO(UUID id, String titulo, LocalDateTime dataHora,
                              Integer limiteAlunos, long vagasDisponiveis,
                              UUID professorId, String professorNome, boolean cancelada,
                              LocalDateTime dataHoraCancelamento, String motivoCancelamento) {}
