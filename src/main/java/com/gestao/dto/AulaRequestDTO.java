package com.gestao.dto;
import jakarta.validation.constraints.*;
import java.time.LocalDateTime;
public record AulaRequestDTO(@NotBlank @Size(max=120) String titulo,
                             @NotNull @Future LocalDateTime dataHora,
                             @Min(8) @Max(8) Integer limiteAlunos) {}
