package com.gestao.security;

import com.gestao.domain.Usuario;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.SignatureAlgorithm;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.security.Key;
import java.util.Date;

@Service
public class TokenService {

    // Lê a palavra-passe secreta do application.properties
    @Value("${api.security.token.secret}")
    private String secret;

    private Key getSigningKey() {
        return Keys.hmacShaKeyFor(secret.getBytes());
    }

    // Método que gera a "pulseira VIP" para o utilizador
    public String gerarToken(Usuario usuario) {
        return Jwts.builder()
                .setIssuer("AgendoFit API")
                .setSubject(usuario.getEmail())
                .claim("role", usuario.getRole().name()) // Guarda se é ALUNO ou PROFESSOR
                .setExpiration(new Date(System.currentTimeMillis() + 86400000)) // Válido por 24 horas
                .signWith(getSigningKey(), SignatureAlgorithm.HS256)
                .compact();
    }

    // Método que lê o token e devolve o email do utilizador que está lá dentro
    public String validarTokenPegandoEmail(String token) {
        try {
            return Jwts.parserBuilder()
                    .setSigningKey(getSigningKey())
                    .build()
                    .parseClaimsJws(token)
                    .getBody()
                    .getSubject();
        } catch (Exception e) {
            throw new RuntimeException("Token JWT inválido ou expirado!");
        }
    }
}