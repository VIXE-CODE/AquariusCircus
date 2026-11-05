package com.gestao.dto;
import jakarta.validation.constraints.Size;
public record CancelamentoAulaDTO(@Size(max = 500) String motivo) {}
