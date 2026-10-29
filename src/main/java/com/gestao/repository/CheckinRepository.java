package com.gestao.repository;
import com.gestao.domain.*;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import java.util.*;
public interface CheckinRepository extends JpaRepository<Checkin, UUID> {
    long countByAulaAndStatusNot(Aula aula, String status);
    Optional<Checkin> findByAlunoAndAula(Usuario aluno, Aula aula);
    List<Checkin> findByAlunoOrderByDataHoraAgendamentoDesc(Usuario aluno);
    Optional<Checkin> findByIdAndAluno(UUID id, Usuario aluno);
    @Query("select c.aula.id from Checkin c where c.id = :id and c.aluno = :aluno")
    Optional<UUID> findAulaIdDaReserva(@Param("id") UUID id, @Param("aluno") Usuario aluno);
}
