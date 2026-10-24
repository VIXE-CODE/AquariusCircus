package com.gestao.repository;

import com.gestao.domain.Contrato;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface ContratoRepository extends JpaRepository<Contrato, UUID> {

    // Busca o contrato ativo de um aluno específico para validar o check-in
    Optional<Contrato> findByAlunoIdAndAtivoTrue(UUID alunoId);
}