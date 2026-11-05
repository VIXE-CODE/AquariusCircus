package com.gestao.dto;
import jakarta.validation.constraints.*;
public record RecuperacaoSenhaDTO(@NotBlank @Email @Size(max=254) String email) {}
