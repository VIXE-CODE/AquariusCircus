package com.gestao.controller;

import com.gestao.domain.Aula;
import com.gestao.domain.Usuario;
import com.gestao.dto.AulaRequestDTO;
import com.gestao.repository.AulaRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;

import java.util.List; // <-- IMPORT ADICIONADO AQUI

@RestController
@RequestMapping("/api/professor")
public class ProfessorController {

    @Autowired
    private AulaRepository aulaRepository;

    @PostMapping("/aulas")
    public ResponseEntity<String> criarAula(@RequestBody AulaRequestDTO data) {

        // 1. Vai buscar o utilizador (Professor) que está autenticado neste momento pelo Token JWT
        Usuario professorLogado = (Usuario) SecurityContextHolder.getContext().getAuthentication().getPrincipal();

        // 2. Cria a entidade Aula e preenche com os dados
        Aula novaAula = new Aula();
        novaAula.setTitulo(data.titulo());
        novaAula.setDataHora(data.dataHora());
        novaAula.setLimiteAlunos(data.limiteAlunos());

        // 3. Associa a aula ao professor que fez o login
        novaAula.setProfessor(professorLogado);

        // 4. Guarda na base de dados (PostgreSQL)
        aulaRepository.save(novaAula);

        return ResponseEntity.status(201).body("Aula '" + data.titulo() + "' criada com sucesso!");
    }

    @GetMapping("/aulas")
    public ResponseEntity<List<Aula>> listarMinhasAulas() {

        // 1. Descobre quem é o professor logado através do Token JWT
        Usuario professorLogado = (Usuario) SecurityContextHolder.getContext().getAuthentication().getPrincipal();

        // 2. Vai à base de dados buscar apenas as aulas deste professor
        List<Aula> minhasAulas = aulaRepository.findByProfessor(professorLogado);

        // 3. Devolve a lista em formato JSON
        return ResponseEntity.ok(minhasAulas);
    }
}