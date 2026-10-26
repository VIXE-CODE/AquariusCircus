package com.gestao.security;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
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

@Configuration
@EnableWebSecurity
public class SecurityConfig {

    // INJEÇÃO DO NOVO FILTRO AQUI
    @Autowired
    private SecurityFilter securityFilter;

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    @Bean
    public AuthenticationManager authenticationManager(AuthenticationConfiguration authenticationConfiguration) throws Exception {
        return authenticationConfiguration.getAuthenticationManager();
    }

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        return http
                .csrf(csrf -> csrf.disable())
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(authorize -> authorize
                        // Rotas públicas de autenticação
                        .requestMatchers(HttpMethod.POST, "/api/auth/login").permitAll()
                        .requestMatchers(HttpMethod.POST, "/api/auth/cadastrar").permitAll()

                        // Rotas do Professor
                        .requestMatchers("/api/professor/**").hasRole("PROFESSOR")

                        // Rotas do Aluno
                        .requestMatchers("/api/aluno/**").hasRole("ALUNO")

                        // --- NOVAS REGRAS PARA PLANOS E CONTRATOS ---
                        // Qualquer pessoa (ou apenas autenticados) pode ver os planos disponíveis
                        .requestMatchers(HttpMethod.GET, "/api/planos").permitAll()
                        // Apenas professores podem criar planos
                        .requestMatchers(HttpMethod.POST, "/api/planos").hasRole("PROFESSOR")

                        // Apenas professores podem gerir/criar contratos
                        .requestMatchers("/api/contratos/**").hasRole("PROFESSOR")

                        // Qualquer outra rota exige que esteja autenticado
                        .anyRequest().authenticated()
                )
                .addFilterBefore(securityFilter, UsernamePasswordAuthenticationFilter.class)
                .build();
    }
}