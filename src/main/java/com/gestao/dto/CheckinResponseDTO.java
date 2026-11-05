package com.gestao.dto;
import java.time.LocalDateTime;
import java.util.UUID;
public record CheckinResponseDTO(UUID id, UUID aulaId, String titulo,
                                 LocalDateTime dataHora, String status,
                                 LocalDateTime dataHoraAgendamento, boolean aulaCancelada) {}
