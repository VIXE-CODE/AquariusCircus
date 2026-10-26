package com.gestao.repository;

import com.gestao.domain.Aula;
import com.gestao.domain.Checkin;
import com.gestao.domain.Usuario;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.UUID;

@Repository
public interface CheckinRepository extends JpaRepository<Checkin, UUID> {

    // Conta quantos check-ins já existem para uma aula específica
    long countByAula(Aula aula);

    // Verifica se este aluno já fez check-in nesta aula
    boolean existsByAlunoAndAula(Usuario aluno, Aula aula);
}