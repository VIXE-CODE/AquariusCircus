package com.gestao.repository;

import com.gestao.domain.Aula;
import com.gestao.domain.Usuario;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface AulaRepository extends JpaRepository<Aula, UUID> {

    // Adicione esta linha:
    List<Aula> findByProfessor(Usuario professor);
}