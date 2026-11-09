package com.gestao.controller;
import com.gestao.domain.Usuario;
import com.gestao.dto.*;
import com.gestao.service.AgendaService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/professor")
public class ProfessorController {
    private final AgendaService agenda;
    public ProfessorController(AgendaService agenda) { this.agenda=agenda; }
    @PostMapping("/aulas")
    public ResponseEntity<AulaResponseDTO> criar(@AuthenticationPrincipal Usuario professor,
                                               @Valid @RequestBody AulaRequestDTO data) {
        return ResponseEntity.status(201).body(agenda.criar(professor, data));
    }
    @GetMapping("/aulas")
    public List<AulaResponseDTO> minhas(@AuthenticationPrincipal Usuario professor) { return agenda.doProfessor(professor); }
    @PatchMapping("/aulas/{id}")
    public AulaResponseDTO editar(@AuthenticationPrincipal Usuario professor, @PathVariable UUID id,
                                 @Valid @RequestBody AulaEdicaoDTO dto) {
        return agenda.editar(professor, id, dto);
    }
    @PatchMapping("/aulas/{id}/cancelar")
    public AulaResponseDTO cancelar(@AuthenticationPrincipal Usuario professor, @PathVariable UUID id,
                                   @Valid @RequestBody(required = false) CancelamentoAulaDTO dto) {
        return agenda.cancelarAula(professor, id, dto);
    }
    @GetMapping("/aulas/{id}/checkins")
    public List<ParticipanteResponseDTO> participantes(@AuthenticationPrincipal Usuario professor, @PathVariable UUID id) {
        return agenda.participantes(professor, id);
    }
    @PatchMapping("/checkins/{id}/presenca")
    public CheckinResponseDTO presenca(@AuthenticationPrincipal Usuario professor, @PathVariable UUID id,
                                      @Valid @RequestBody PresencaRequestDTO dto) {
        return agenda.registrarPresenca(professor, id, dto);
    }
}
