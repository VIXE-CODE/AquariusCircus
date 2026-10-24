package com.gestao.domain;

import jakarta.persistence.*;
import lombok.Data;
import java.time.LocalDate;
import java.util.UUID;

@Entity
@Data
@Table(name = "pagamentos")
public class Pagamento {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne
    @JoinColumn(name = "contrato_id")
    private Contrato contrato;

    private Double valorCobrado;
    private Double valorPago; // Pode ser preenchido quando o aluno efetivamente pagar

    private LocalDate dataVencimento;
    private LocalDate dataPagamento;

    // Status possíveis: PENDENTE, PAGO, ATRASADO
    private String status;
}