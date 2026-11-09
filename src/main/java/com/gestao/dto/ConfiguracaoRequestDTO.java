package com.gestao.dto;
import jakarta.validation.constraints.*;
public record ConfiguracaoRequestDTO(@NotNull @Min(0) @Max(8760) Integer antecedenciaCancelamentoHoras) {}
