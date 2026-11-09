package com.gestao.domain;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import java.time.Instant;
import java.util.UUID;
@Entity
@Getter @Setter
@Table(name="recuperacoes_senha")
public class RecuperacaoSenha {
    @Id @GeneratedValue(strategy=GenerationType.UUID) private UUID id;
    @OneToOne @JoinColumn(name="usuario_id",nullable=false,unique=true) private Usuario usuario;
    @Column(nullable=false,unique=true,length=64) private String tokenHash;
    @Column(nullable=false) private Instant criadoEm;
    @Column(nullable=false) private Instant expiraEm;
    private Instant utilizadoEm;
}
