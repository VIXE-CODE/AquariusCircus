package com.gestao.security;

import com.gestao.repository.UsuarioRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;

@Service
public class AutenticacaoService implements UserDetailsService {

    @Autowired
    private UsuarioRepository repository;

    @Override
    public UserDetails loadUserByUsername(String username) throws UsernameNotFoundException {
        // Busca o utilizador no PostgreSQL pelo e-mail
        return repository.findByEmail(username)
                .orElseThrow(() -> new UsernameNotFoundException("Utilizador não encontrado com o e-mail: " + username));
    }
}