package com.gestao.security;
import com.gestao.domain.Usuario;
import io.jsonwebtoken.*;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import java.nio.charset.StandardCharsets;
import java.security.Key;
import java.util.Date;

@Service
public class TokenService {
    public static final long EXPIRACAO_SEGUNDOS = 86400;
    private final Key signingKey;
    public TokenService(@Value("${api.security.token.secret}") String secret) {
        this.signingKey = Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
    }
    public String gerarToken(Usuario usuario) {
        return Jwts.builder().setIssuer("Aquarius Circus API")
            .setSubject(usuario.getEmail()).claim("role", usuario.getRole().name())
            .setIssuedAt(new Date())
            .setExpiration(new Date(System.currentTimeMillis() + EXPIRACAO_SEGUNDOS * 1000))
            .signWith(signingKey, SignatureAlgorithm.HS256).compact();
    }
    public String validarTokenPegandoEmail(String token) {
        return Jwts.parserBuilder().setSigningKey(signingKey).requireIssuer("Aquarius Circus API")
            .build().parseClaimsJws(token).getBody().getSubject();
    }
}
