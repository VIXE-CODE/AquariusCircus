package com.gestao.repository;
import com.gestao.domain.RecuperacaoSenha;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;
import java.util.*;
public interface RecuperacaoSenhaRepository extends JpaRepository<RecuperacaoSenha,UUID> {
    Optional<RecuperacaoSenha> findByUsuarioId(UUID usuarioId);
    Optional<RecuperacaoSenha> findByTokenHash(String tokenHash);
    @Query("select r.usuario.id from RecuperacaoSenha r where r.tokenHash = :hash")
    Optional<UUID> findUsuarioId(@Param("hash") String hash);
}
