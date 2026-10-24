package com.gestao.repository;

import com.gestao.domain.Plano;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.util.UUID;

@Repository
public interface PlanoRepository extends JpaRepository<Plano, UUID> {
}