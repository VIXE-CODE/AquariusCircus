package com.gestao.repository;

import com.gestao.domain.Checkin;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.util.List;
import java.util.UUID;

@Repository
public interface CheckinRepository extends JpaRepository<Checkin, UUID> {

    // Conta quantos alunos já estão confirmados (AGENDADO ou PRESENTE) em uma aula específica
    int countByAulaIdAndStatusIn(UUID aulaId, List<String> status);
}