package com.gestao.dto;
import jakarta.validation.constraints.*;
import java.time.LocalDateTime;
public record AulaEdicaoDTO(@NotBlank @Size(max = 120) String titulo, @NotNull @Future LocalDateTime dataHora) {}
