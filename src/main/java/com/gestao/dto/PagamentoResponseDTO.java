package com.gestao.dto;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;
public record PagamentoResponseDTO(UUID id, Integer numeroParcela, BigDecimal valorCobrado,
    BigDecimal valorPago, LocalDate dataVencimento, LocalDate dataPagamento, String status) {}
