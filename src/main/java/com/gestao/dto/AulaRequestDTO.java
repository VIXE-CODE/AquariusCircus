package com.gestao.dto;

import java.time.LocalDateTime;

public record AulaRequestDTO(String titulo, LocalDateTime dataHora, Integer limiteAlunos) {
}