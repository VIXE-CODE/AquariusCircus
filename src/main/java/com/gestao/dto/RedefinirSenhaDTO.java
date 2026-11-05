package com.gestao.dto;
import jakarta.validation.constraints.*;
public record RedefinirSenhaDTO(@NotBlank @Pattern(regexp="[A-Za-z0-9_-]{43}") String token,
    @NotBlank @Size(min=8,max=72) String novaSenha) {}
