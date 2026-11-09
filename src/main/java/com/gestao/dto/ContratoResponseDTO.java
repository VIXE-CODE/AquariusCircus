package com.gestao.dto;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.*;
public record ContratoResponseDTO(UUID id, UUID alunoId, String planoNome, Integer aulasContratadas,
    long creditosConsumidos, long creditosDisponiveis, LocalDate dataInicio, LocalDate dataVencimento,
    BigDecimal valorMensal, String situacao, List<PagamentoResponseDTO> parcelas) {}
