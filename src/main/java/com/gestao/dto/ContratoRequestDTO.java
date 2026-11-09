package com.gestao.dto;
import jakarta.validation.constraints.*;
import java.math.BigDecimal;
import java.util.UUID;
public record ContratoRequestDTO(@NotNull UUID alunoId, @NotNull UUID planoId,
    @DecimalMin("0.01") @Digits(integer = 8, fraction = 2) BigDecimal valorMensal) {}
