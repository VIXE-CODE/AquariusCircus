package com.gestao.dto;
import jakarta.validation.constraints.*;
import java.time.LocalDate;
public record PagamentoRequestDTO(@NotNull @PastOrPresent LocalDate dataPagamento) {}
