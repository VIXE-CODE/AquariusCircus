package com.gestao.controller;
import com.gestao.domain.Usuario;
import com.gestao.dto.*;
import com.gestao.security.TokenService;
import com.gestao.service.CadastroService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.*;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/auth")
public class AuthController {
    private final AuthenticationManager authenticationManager;
    private final TokenService tokens;
    private final CadastroService cadastro;
    public AuthController(AuthenticationManager authenticationManager, TokenService tokens, CadastroService cadastro) {
        this.authenticationManager=authenticationManager; this.tokens=tokens; this.cadastro=cadastro;
    }
    @PostMapping("/login")
    public LoginResponseDTO login(@Valid @RequestBody LoginDTO data) {
        var auth=authenticationManager.authenticate(new UsernamePasswordAuthenticationToken(
            CadastroService.normalizarEmail(data.email()), data.senha()));
        var usuario=(Usuario)auth.getPrincipal();
        return new LoginResponseDTO(tokens.gerarToken(usuario), "Bearer", TokenService.EXPIRACAO_SEGUNDOS,
            UsuarioResponseDTO.from(usuario));
    }
    @PostMapping("/cadastrar")
    public ResponseEntity<UsuarioResponseDTO> cadastrar(@Valid @RequestBody CadastroDTO data) {
        return ResponseEntity.status(201).body(UsuarioResponseDTO.from(cadastro.cadastrar(data)));
    }
    @GetMapping("/me")
    public UsuarioResponseDTO me(@AuthenticationPrincipal Usuario usuario) {
        return UsuarioResponseDTO.from(usuario);
    }
}
