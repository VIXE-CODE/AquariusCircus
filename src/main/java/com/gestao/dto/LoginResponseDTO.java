package com.gestao.dto;
public record LoginResponseDTO(String token, String tipo, long expiraEmSegundos,
                               UsuarioResponseDTO usuario) {}
