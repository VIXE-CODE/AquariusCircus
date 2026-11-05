package com.gestao.repository;
import com.gestao.domain.*;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;
import java.time.LocalDateTime;
import java.util.*;
public interface AulaRepository extends JpaRepository<Aula, UUID> {
    List<Aula> findByProfessorOrderByDataHoraAsc(Usuario professor);
    @Query("select a from Aula a where a.dataHora > :agora and coalesce(a.cancelada, false) = false order by a.dataHora asc")
    List<Aula> findDisponiveis(@Param("agora") LocalDateTime agora);
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select a from Aula a where a.id = :id")
    Optional<Aula> findComBloqueioById(@Param("id") UUID id);
}
