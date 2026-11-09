package com.gestao.domain;
import jakarta.persistence.*;
import lombok.Data;
@Entity
@Data
@Table(name = "configuracao_estudio")
public class ConfiguracaoEstudio {
    @Id private Long id = 1L;
    @Column(nullable = false)
    private Integer antecedenciaCancelamentoHoras = 12;
}
