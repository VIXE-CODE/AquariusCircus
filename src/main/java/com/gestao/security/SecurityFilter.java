package com.gestao.security;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.gestao.dto.ApiError;
import com.gestao.repository.UsuarioRepository;
import io.jsonwebtoken.JwtException;
import jakarta.servlet.*;
import jakarta.servlet.http.*;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;
import java.io.IOException;

@Component
public class SecurityFilter extends OncePerRequestFilter {
    private final TokenService tokens;
    private final UsuarioRepository usuarios;
    private final ObjectMapper mapper;
    public SecurityFilter(TokenService tokens, UsuarioRepository usuarios, ObjectMapper mapper) {
        this.tokens=tokens; this.usuarios=usuarios; this.mapper=mapper;
    }
    @Override
    protected void doFilterInternal(HttpServletRequest req, HttpServletResponse res, FilterChain chain)
            throws ServletException, IOException {
        String header = req.getHeader("Authorization");
        if (header != null) {
            String email;
            try {
                if (!header.startsWith("Bearer ")) throw new IllegalArgumentException();
                email = tokens.validarTokenPegandoEmail(header.substring(7));
                if (email == null || email.isBlank()) throw new IllegalArgumentException();
            } catch (JwtException | IllegalArgumentException e) {
                unauthorized(res); return;
            }
            var usuario = usuarios.findByEmail(email).orElse(null);
            if (usuario == null || !usuario.isEnabled() || !usuario.isAccountNonLocked()) {
                unauthorized(res); return;
            }
            var auth = new UsernamePasswordAuthenticationToken(usuario, null, usuario.getAuthorities());
            SecurityContextHolder.getContext().setAuthentication(auth);
        }
        chain.doFilter(req, res);
    }
    private void unauthorized(HttpServletResponse res) throws IOException {
        SecurityContextHolder.clearContext();
        res.setStatus(401); res.setContentType("application/json"); res.setCharacterEncoding("UTF-8");
        mapper.writeValue(res.getWriter(), new ApiError(401, "Token inválido ou expirado."));
    }
}
