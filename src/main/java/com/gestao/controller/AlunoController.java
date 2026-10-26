package com.gestao.controller;

import com.gestao.domain.Aula;
import com.gestao.domain.Checkin;
import com.gestao.domain.Usuario;
import com.gestao.dto.CheckinRequestDTO;
import com.gestao.repository.AulaRepository;
import com.gestao.repository.CheckinRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.List;

@RestController
@RequestMapping("/api/aluno")
public class AlunoController {

    @Autowired
    private AulaRepository aulaRepository;

    @Autowired
    private CheckinRepository checkinRepository;

    @GetMapping("/aulas")
    public ResponseEntity<List<Aula>> listarAulasDisponiveis() {
        // Traz todas as aulas cadastradas no sistema
        return ResponseEntity.ok(aulaRepository.findAll());
    }

    @PostMapping("/checkin")
    public ResponseEntity<String> fazerCheckin(@RequestBody CheckinRequestDTO data) {

        // 1. Identifica o Aluno logado através do Token JWT
        Usuario alunoLogado = (Usuario) SecurityContextHolder.getContext().getAuthentication().getPrincipal();

        // 2. Procura a aula pelo ID recebido no JSON
        Aula aula = aulaRepository.findById(data.aulaId())
                .orElse(null);

        if (aula == null) {
            return ResponseEntity.badRequest().body("Erro: Aula não encontrada.");
        }

        // 3. REGRA DE NEGÓCIO: Verifica se o aluno já fez check-in nesta mesma aula
        if (checkinRepository.existsByAlunoAndAula(alunoLogado, aula)) {
            return ResponseEntity.badRequest().body("Erro: Já tem o seu lugar marcado nesta aula!");
        }

        // 4. REGRA DE NEGÓCIO: Verifica se a aula já está lotada
        long vagasOcupadas = checkinRepository.countByAula(aula);
        if (vagasOcupadas >= aula.getLimiteAlunos()) {
            return ResponseEntity.badRequest().body("Erro: A aula já atingiu o limite máximo de alunos.");
        }

        // 5. Se passou em todas as regras, cria o check-in!
        Checkin novoCheckin = new Checkin();
        novoCheckin.setAluno(alunoLogado);
        novoCheckin.setAula(aula);
        novoCheckin.setDataHoraAgendamento(LocalDateTime.now());
        novoCheckin.setStatus("CONFIRMADO");

        checkinRepository.save(novoCheckin);

        return ResponseEntity.status(201).body("Check-in confirmado com sucesso na aula: " + aula.getTitulo());
    }
}