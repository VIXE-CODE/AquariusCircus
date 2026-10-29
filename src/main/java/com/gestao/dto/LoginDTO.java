package com.gestao.dto;
import jakarta.validation.constraints.*;
public record LoginDTO(@NotBlank @Email String email, @NotBlank String senha) {}
