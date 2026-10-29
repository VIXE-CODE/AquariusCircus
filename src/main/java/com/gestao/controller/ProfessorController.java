package com.gestao.controller;
import com.gestao.domain.Usuario;
import com.gestao.dto.*;
import com.gestao.service.AgendaService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import java.util.List;

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
}
