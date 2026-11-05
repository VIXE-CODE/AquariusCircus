package com.gestao.repository;
import com.gestao.domain.*;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.repository.query.Param;
import java.util.*;
public interface CheckinRepository extends JpaRepository<Checkin, UUID> {
    long countByAulaAndStatusNot(Aula aula, String status);
    long countByAulaAndStatusIn(Aula aula, Collection<String> status);
    long countByContratoAndStatusIn(Contrato contrato, Collection<String> status);
    boolean existsByAlunoAndContratoIsNullAndStatusInAndAulaDataHoraAfter(
        Usuario aluno, Collection<String> status, java.time.LocalDateTime data);
    Optional<Checkin> findByAlunoAndAula(Usuario aluno, Aula aula);
    List<Checkin> findByAulaOrderByDataHoraAgendamentoAsc(Aula aula);
    List<Checkin> findByAlunoOrderByDataHoraAgendamentoDesc(Usuario aluno);
    Optional<Checkin> findByIdAndAluno(UUID id, Usuario aluno);
    @Query("select c.aula.id from Checkin c where c.id = :id and c.aluno = :aluno")
    Optional<UUID> findAulaIdDaReserva(@Param("id") UUID id, @Param("aluno") Usuario aluno);
    @Query("select c.aluno.id from Checkin c where c.id = :id")
    Optional<UUID> findAlunoId(@Param("id") UUID id);
    @Modifying(flushAutomatically = true)
    @Query("update Checkin c set c.status = 'CANCELADO_AULA' where c.aula.id = :aulaId and c.status in ('CONFIRMADO', 'PRESENTE', 'FALTA')")
    int cancelarPelaEscola(@Param("aulaId") UUID aulaId);
}
