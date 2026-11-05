package com.gestao.dto;
import jakarta.validation.constraints.*;
public record AlterarSenhaDTO(@NotBlank String senhaAtual, @NotBlank @Size(min=8,max=72) String novaSenha) {}
