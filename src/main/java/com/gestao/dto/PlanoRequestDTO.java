package com.gestao.dto;
import jakarta.validation.constraints.*;
import java.math.BigDecimal;
public record PlanoRequestDTO(@NotBlank @Size(max = 120) String nome,
                              @NotNull Integer aulasPorMes,
                              @NotNull Integer duracaoMeses,
                              @NotNull @DecimalMin("0.01") @Digits(integer = 8, fraction = 2) BigDecimal valorMensal) {
    @AssertTrue(message = "Use 4, 8 ou 12 aulas por mês e duração de 1 ou 3 meses.")
    public boolean isPacoteValido() {
        return aulasPorMes != null && java.util.List.of(4, 8, 12).contains(aulasPorMes)
            && duracaoMeses != null && java.util.List.of(1, 3).contains(duracaoMeses);
    }
}
