package com.gestao.controller;
import com.gestao.domain.Usuario;
import com.gestao.dto.*;
import com.gestao.service.AgendaService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import java.util.*;

@RestController
@RequestMapping("/api/aluno")
public class AlunoController {
    private final AgendaService agenda;
    public AlunoController(AgendaService agenda) { this.agenda=agenda; }
    @GetMapping("/aulas") public List<AulaResponseDTO> aulas() { return agenda.disponiveis(); }
    @PostMapping("/checkin")
    public ResponseEntity<CheckinResponseDTO> reservar(@AuthenticationPrincipal Usuario aluno,
                                                      @Valid @RequestBody CheckinRequestDTO data) {
        return ResponseEntity.status(201).body(agenda.reservar(aluno, data.aulaId()));
    }
    @GetMapping("/checkins")
    public List<CheckinResponseDTO> minhas(@AuthenticationPrincipal Usuario aluno) { return agenda.minhasReservas(aluno); }
    @PatchMapping("/checkins/{id}/cancelar")
    public CheckinResponseDTO cancelar(@AuthenticationPrincipal Usuario aluno, @PathVariable UUID id) {
        return agenda.cancelar(aluno, id);
    }
}
