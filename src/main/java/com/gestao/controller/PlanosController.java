package com.gestao.controller;
import com.gestao.domain.Usuario;
import com.gestao.dto.*;
import com.gestao.service.*;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import java.util.*;

@RestController
public class PlanosController {
    private final PlanoFinanceiroService financeiro;
    private final ConfiguracaoService configuracoes;
    public PlanosController(PlanoFinanceiroService financeiro, ConfiguracaoService configuracoes) {
        this.financeiro=financeiro; this.configuracoes=configuracoes;
    }
    @GetMapping("/api/planos") public List<PlanoResponseDTO> planos() { return financeiro.listarPlanos(); }
    @PostMapping("/api/professor/planos")
    public ResponseEntity<PlanoResponseDTO> criar(@Valid @RequestBody PlanoRequestDTO dto) {
        return ResponseEntity.status(201).body(financeiro.criarPlano(dto));
    }
    @PostMapping("/api/professor/contratos")
    public ResponseEntity<ContratoResponseDTO> contratar(@AuthenticationPrincipal Usuario professor,
        @Valid @RequestBody ContratoRequestDTO dto) {
        return ResponseEntity.status(201).body(financeiro.contratar(professor, dto));
    }
    @GetMapping("/api/professor/contratos")
    public List<ContratoResponseDTO> contratosProfessor(@AuthenticationPrincipal Usuario professor) {
        return financeiro.doProfessor(professor);
    }
    @GetMapping("/api/aluno/contratos")
    public List<ContratoResponseDTO> contratosAluno(@AuthenticationPrincipal Usuario aluno) {
        return financeiro.doAluno(aluno);
    }
    @PostMapping("/api/professor/contratos/{id}/primeiro-pagamento")
    public ContratoResponseDTO primeiroPagamento(@AuthenticationPrincipal Usuario professor,
        @PathVariable UUID id, @Valid @RequestBody PagamentoRequestDTO dto) {
        return financeiro.primeiroPagamento(professor, id, dto);
    }
    @PostMapping("/api/professor/pagamentos/{id}/confirmar")
    public PagamentoResponseDTO confirmar(@AuthenticationPrincipal Usuario professor,
        @PathVariable UUID id, @Valid @RequestBody PagamentoRequestDTO dto) {
        return financeiro.confirmarPagamento(professor, id, dto);
    }
    @GetMapping("/api/configuracoes") public ConfiguracaoResponseDTO configuracoes() { return configuracoes.consultar(); }
    @PatchMapping("/api/professor/configuracoes")
    public ConfiguracaoResponseDTO configurar(@Valid @RequestBody ConfiguracaoRequestDTO dto) {
        return configuracoes.atualizar(dto);
    }
}
