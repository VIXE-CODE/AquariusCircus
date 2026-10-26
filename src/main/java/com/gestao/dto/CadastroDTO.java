package com.gestao.dto;

import com.gestao.domain.Role;

// Para o cadastro inicial, pedimos apenas o básico.
// A ficha técnica do aluno pode ser preenchida depois pelo professor.
public record CadastroDTO(String nome, String email, String senha, Role role) {
}