package com.gestao.controller;
import com.gestao.domain.Usuario;
import com.gestao.dto.*;
import com.gestao.service.SenhaService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
@RestController
@RequestMapping("/api/auth")
public class SenhaController {
    private final SenhaService senhas;
    public SenhaController(SenhaService senhas) { this.senhas=senhas; }
    @PostMapping("/recuperar-senha")
    public ResponseEntity<MensagemDTO> solicitar(@Valid @RequestBody RecuperacaoSenhaDTO dto) {
        senhas.solicitar(dto);
        return ResponseEntity.accepted().body(new MensagemDTO("Se o e-mail estiver cadastrado, enviaremos as instruções de recuperação."));
    }
    @PostMapping("/redefinir-senha")
    public MensagemDTO redefinir(@Valid @RequestBody RedefinirSenhaDTO dto) {
        senhas.redefinir(dto);return new MensagemDTO("Senha atualizada. Faça login novamente.");
    }
    @PatchMapping("/senha")
    public MensagemDTO alterar(@AuthenticationPrincipal Usuario usuario,@Valid @RequestBody AlterarSenhaDTO dto) {
        senhas.alterar(usuario,dto);return new MensagemDTO("Senha atualizada. Faça login novamente.");
    }
}
