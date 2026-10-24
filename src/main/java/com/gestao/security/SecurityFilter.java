package com.gestao.security;

import com.gestao.repository.UsuarioRepository;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

@Component
public class SecurityFilter extends OncePerRequestFilter {

    @Autowired
    private TokenService tokenService;

    @Autowired
    private UsuarioRepository usuarioRepository;

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain) throws ServletException, IOException {
        // 1. Tenta extrair o token do cabeçalho da requisição
        String token = recuperarToken(request);

        if (token != null) {
            // 2. Se houver token, valida-o e extrai o e-mail
            String email = tokenService.validarTokenPegandoEmail(token);

            // 3. Busca o utilizador no banco de dados
            var usuario = usuarioRepository.findByEmail(email).orElse(null);

            if (usuario != null) {
                // 4. Cria a autenticação e injeta no contexto do Spring Security
                var authentication = new UsernamePasswordAuthenticationToken(usuario, null, usuario.getAuthorities());
                SecurityContextHolder.getContext().setAuthentication(authentication);
            }
        }

        // 5. Continua o fluxo normal da requisição (passa para o próximo filtro ou para o Controller)
        filterChain.doFilter(request, response);
    }

    private String recuperarToken(HttpServletRequest request) {
        var authHeader = request.getHeader("Authorization");
        if (authHeader == null || !authHeader.startsWith("Bearer ")) {
            return null;
        }
        // Remove a palavra "Bearer " e devolve apenas o hash do token
        return authHeader.replace("Bearer ", "");
    }
}