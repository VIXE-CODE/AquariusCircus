package com.gestao.repository;

import com.gestao.domain.Aula;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface AulaRepository extends JpaRepository<Aula, UUID> {

    // Busca todas as aulas filtrando pelo ID do professor que as criou
    List<Aula> findByProfessorId(UUID professorId);
}