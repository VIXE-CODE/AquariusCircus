package com.gestao.dto;
import jakarta.validation.constraints.*;
public record PresencaRequestDTO(@NotBlank @Pattern(regexp = "PRESENTE|FALTA") String status) {}
