package com.gestao.dto;
import com.gestao.domain.Role;
import com.gestao.domain.Usuario;
import java.util.UUID;
public record UsuarioResponseDTO(UUID id, String nome, String email, Role role) {
    public static UsuarioResponseDTO from(Usuario u) {
        return new UsuarioResponseDTO(u.getId(), u.getNome(), u.getEmail(), u.getRole());
    }
}
