package com.gestao.repository;

import com.gestao.domain.Contrato;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.util.Optional;
import java.util.UUID;
import java.util.List;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

@Repository
public interface ContratoRepository extends JpaRepository<Contrato, UUID> {

    // Busca o contrato ativo de um aluno específico para validar o check-in
    List<Contrato> findByAlunoIdOrderByDataInicioDesc(UUID alunoId);
    List<Contrato> findByProfessorId(UUID professorId);
    @Query("select c.aluno.id from Contrato c where c.id = :id")
    Optional<UUID> findAlunoId(@Param("id") UUID id);
}
