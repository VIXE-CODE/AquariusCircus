package com.gestao.repository;

import com.gestao.domain.Pagamento;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.util.UUID;

@Repository
public interface PagamentoRepository extends JpaRepository<Pagamento, UUID> {
}