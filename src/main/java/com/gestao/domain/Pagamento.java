package com.gestao.domain;

import jakarta.persistence.*;
import lombok.Data;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

@Entity
@Data
@Table(name = "pagamentos", uniqueConstraints = @UniqueConstraint(
    name = "uk_pagamento_contrato_parcela", columnNames = {"contrato_id", "numero_parcela"}))
public class Pagamento {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne
    @JoinColumn(name = "contrato_id")
    private Contrato contrato;

    private Integer numeroParcela;
    @Column(precision = 10, scale = 2)
    private BigDecimal valorCobrado;
    @Column(precision = 10, scale = 2)
    private BigDecimal valorPago;

    private LocalDate dataVencimento;
    private LocalDate dataPagamento;

    // Status possíveis: PENDENTE, PAGO, ATRASADO
    private String status;
}
