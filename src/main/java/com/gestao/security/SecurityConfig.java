package com.gestao.security;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.gestao.dto.ApiError;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.*;
import org.springframework.http.HttpMethod;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.config.annotation.authentication.configuration.AuthenticationConfiguration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.cors.*;
import java.util.Arrays;
import java.util.List;

@Configuration
@EnableWebSecurity
public class SecurityConfig {
    @Bean PasswordEncoder passwordEncoder() { return new BCryptPasswordEncoder(); }
    @Bean AuthenticationManager authenticationManager(AuthenticationConfiguration config) throws Exception {
        return config.getAuthenticationManager();
    }
    @Bean
    SecurityFilterChain securityFilterChain(HttpSecurity http, SecurityFilter filter, ObjectMapper mapper) throws Exception {
        return http.csrf(csrf -> csrf.disable())
            .cors(cors -> {})
            .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
            .exceptionHandling(errors -> errors
                .authenticationEntryPoint((req, res, e) -> {
                    res.setStatus(401); res.setContentType("application/json"); res.setCharacterEncoding("UTF-8");
                    mapper.writeValue(res.getWriter(), new ApiError(401, "Autenticação necessária."));
                })
                .accessDeniedHandler((req, res, e) -> {
                    res.setStatus(403); res.setContentType("application/json"); res.setCharacterEncoding("UTF-8");
                    mapper.writeValue(res.getWriter(), new ApiError(403, "Acesso não permitido para este perfil."));
                }))
            .authorizeHttpRequests(auth -> auth
                .requestMatchers(HttpMethod.POST, "/api/auth/login", "/api/auth/cadastrar").permitAll()
                .requestMatchers("/api/professor/**").hasAuthority("PROFESSOR")
                .requestMatchers("/api/aluno/**").hasAuthority("ALUNO")
                .requestMatchers(HttpMethod.GET, "/api/planos").permitAll()
                .requestMatchers(HttpMethod.POST, "/api/planos").hasAuthority("PROFESSOR")
                .requestMatchers("/api/contratos/**").hasAuthority("PROFESSOR")
                .anyRequest().authenticated())
            .addFilterBefore(filter, UsernamePasswordAuthenticationFilter.class).build();
    }
    @Bean
    CorsConfigurationSource corsConfigurationSource(@Value("${app.cors.allowed-origins}") String origins) {
        var config = new CorsConfiguration();
        config.setAllowedOrigins(Arrays.stream(origins.split(",")).map(String::trim).filter(s -> !s.isEmpty()).toList());
        config.setAllowedMethods(List.of("GET", "POST", "PATCH", "OPTIONS"));
        config.setAllowedHeaders(List.of("Authorization", "Content-Type"));
        var source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/api/**", config);
        return source;
    }
}
