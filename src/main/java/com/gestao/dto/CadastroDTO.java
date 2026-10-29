package com.gestao.dto;
import com.gestao.domain.Role;
import jakarta.validation.constraints.*;
public record CadastroDTO(@NotBlank @Size(max=120) String nome,
                          @NotBlank @Email @Size(max=254) String email,
                          @NotBlank @Size(min=8, max=72) String senha, Role role) {}
