package com.gestao.dto;
import com.gestao.domain.Plano;
import java.math.BigDecimal;
import java.util.UUID;
public record PlanoResponseDTO(UUID id, String nome, Integer aulasPorMes, Integer duracaoMeses,
                               Integer aulasTotais, BigDecimal valorMensal) {
    public static PlanoResponseDTO from(Plano p) {
        return new PlanoResponseDTO(p.getId(), p.getNome(), p.getAulasPorMes(), p.getDuracaoMeses(),
            p.getAulasPorMes() == null || p.getDuracaoMeses() == null ? null : p.getAulasPorMes() * p.getDuracaoMeses(),
            p.getValorPadrao());
    }
}
