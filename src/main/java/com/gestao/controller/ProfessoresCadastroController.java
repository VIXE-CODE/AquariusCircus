package com.gestao.controller;
import com.gestao.domain.Usuario;
import com.gestao.dto.*;
import com.gestao.service.CadastroService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
@RestController
@RequestMapping("/api/professor/professores")
public class ProfessoresCadastroController {
    private final CadastroService cadastro;
    public ProfessoresCadastroController(CadastroService cadastro) { this.cadastro=cadastro; }
    @PostMapping
    public ResponseEntity<UsuarioResponseDTO> criar(@AuthenticationPrincipal Usuario responsavel,
                                                   @Valid @RequestBody ProfessorCadastroDTO dto) {
        return ResponseEntity.status(201).body(UsuarioResponseDTO.from(cadastro.cadastrarProfessor(responsavel,dto)));
    }
}
