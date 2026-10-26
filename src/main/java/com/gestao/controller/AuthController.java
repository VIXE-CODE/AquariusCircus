package com.gestao.controller;

import com.gestao.domain.Usuario;
import com.gestao.dto.CadastroDTO;
import com.gestao.dto.LoginDTO;
import com.gestao.repository.UsuarioRepository;
import com.gestao.security.TokenService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/auth")
public class AuthController {

    @Autowired
    private AuthenticationManager authenticationManager;

    @Autowired
    private UsuarioRepository usuarioRepository;

    @Autowired
    private TokenService tokenService;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @PostMapping("/login")
    public ResponseEntity<String> login(@RequestBody LoginDTO data) {
        // 1. Prepara as credenciais para o Spring Security validar
        var usernamePassword = new UsernamePasswordAuthenticationToken(data.email(), data.senha());

        // 2. Tenta autenticar (vai chamar aquele nosso AutenticacaoService)
        var auth = this.authenticationManager.authenticate(usernamePassword);

        // 3. Se passou, gera o token JWT
        var token = tokenService.gerarToken((Usuario) auth.getPrincipal());

        return ResponseEntity.ok(token);
    }

    @PostMapping("/cadastrar")
    public ResponseEntity<String> cadastrar(@RequestBody CadastroDTO data) {
        // 1. Verifica se o e-mail já existe no banco
        if (this.usuarioRepository.findByEmail(data.email()).isPresent()) {
            return ResponseEntity.badRequest().body("Erro: Este e-mail já está cadastrado.");
        }

        // 2. Cria um novo usuário e criptografa a senha antes de salvar
        Usuario novoUsuario = new Usuario();
        novoUsuario.setNome(data.nome());
        novoUsuario.setEmail(data.email());
        novoUsuario.setSenha(passwordEncoder.encode(data.senha()));
        novoUsuario.setRole(data.role());

        this.usuarioRepository.save(novoUsuario);

        return ResponseEntity.status(201).body("Usuário cadastrado com sucesso!");
    }
}