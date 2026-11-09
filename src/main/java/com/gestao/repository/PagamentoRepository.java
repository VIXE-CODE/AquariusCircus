package com.gestao.repository;

import com.gestao.domain.Pagamento;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.util.UUID;
import java.time.LocalDate;
import java.util.*;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

@Repository
public interface PagamentoRepository extends JpaRepository<Pagamento, UUID> {
    List<Pagamento> findByContratoIdOrderByNumeroParcelaAsc(UUID contratoId);
    boolean existsByContratoIdAndStatusNotAndDataVencimentoLessThanEqual(UUID contratoId, String status, LocalDate data);
    boolean existsByContratoAlunoIdAndStatusNotAndDataVencimentoLessThanEqual(UUID alunoId, String status, LocalDate data);
    @Query("select p.contrato.aluno.id from Pagamento p where p.id = :id")
    Optional<UUID> findAlunoId(@Param("id") UUID id);
}
