package com.gestao.service;
import com.gestao.domain.*;
import com.gestao.dto.*;
import com.gestao.repository.*;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import java.nio.charset.StandardCharsets;
import java.security.*;
import java.time.*;
import java.util.*;
@Service
public class SenhaService {
    private final UsuarioRepository usuarios;
    private final RecuperacaoSenhaRepository recuperacoes;
    private final PasswordEncoder encoder;
    private final EmailRecuperacaoService email;
    private final ApplicationEventPublisher eventos;
    private final Clock clock;
    private final SecureRandom random=new SecureRandom();
    public SenhaService(UsuarioRepository usuarios,RecuperacaoSenhaRepository recuperacoes,PasswordEncoder encoder,
                        EmailRecuperacaoService email,ApplicationEventPublisher eventos,Clock clock) {
        this.usuarios=usuarios;this.recuperacoes=recuperacoes;this.encoder=encoder;
        this.email=email;this.eventos=eventos;this.clock=clock;
    }
    @Transactional
    public void solicitar(RecuperacaoSenhaDTO dto) {
        if (!email.configurado()) throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE,"Recuperação por e-mail ainda não configurada.");
        var encontrado=usuarios.findByEmailIgnoreCase(CadastroService.normalizarEmail(dto.email())).orElse(null);
        if (encontrado==null) return;
        var usuario=bloquear(encontrado.getId());
        var recuperacao=recuperacoes.findByUsuarioId(usuario.getId()).orElseGet(RecuperacaoSenha::new);
        Instant agora=clock.instant();
        if (recuperacao.getCriadoEm()!=null && recuperacao.getCriadoEm().plusSeconds(60).isAfter(agora)) return;
        byte[] bytes=new byte[32];random.nextBytes(bytes);
        String token=Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
        recuperacao.setUsuario(usuario);recuperacao.setTokenHash(hash(token));recuperacao.setCriadoEm(agora);
        recuperacao.setExpiraEm(agora.plus(Duration.ofMinutes(15)));recuperacao.setUtilizadoEm(null);
        recuperacoes.saveAndFlush(recuperacao);
        eventos.publishEvent(new RecuperacaoSolicitada(usuario.getEmail(),token));
    }
    @Transactional
    public void redefinir(RedefinirSenhaDTO dto) {
        CadastroService.validarSenha(dto.novaSenha());
        String hash=hash(dto.token());
        UUID id=recuperacoes.findUsuarioId(hash).orElseThrow(this::tokenInvalido);
        var usuario=bloquear(id);
        var recuperacao=recuperacoes.findByTokenHash(hash).orElseThrow(this::tokenInvalido);
        if (recuperacao.getUtilizadoEm()!=null || !clock.instant().isBefore(recuperacao.getExpiraEm())) throw tokenInvalido();
        atualizarSenha(usuario,dto.novaSenha());
        recuperacao.setUtilizadoEm(clock.instant());recuperacoes.saveAndFlush(recuperacao);
    }
    @Transactional
    public void alterar(Usuario autenticado,AlterarSenhaDTO dto) {
        CadastroService.validarSenha(dto.novaSenha());
        var usuario=bloquear(autenticado.getId());
        if (!encoder.matches(dto.senhaAtual(),usuario.getSenha()))
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"Senha atual incorreta.");
        atualizarSenha(usuario,dto.novaSenha());
        recuperacoes.findByUsuarioId(usuario.getId()).ifPresent(r->{r.setUtilizadoEm(clock.instant());recuperacoes.save(r);});
    }
    private void atualizarSenha(Usuario usuario,String novaSenha) {
        usuario.setSenha(encoder.encode(novaSenha));
        usuario.setSenhaVersao((usuario.getSenhaVersao()==null?0:usuario.getSenhaVersao())+1);
        usuarios.saveAndFlush(usuario);
    }
    private Usuario bloquear(UUID id) {
        return usuarios.findComBloqueioById(id).orElseThrow(()->new ResponseStatusException(HttpStatus.NOT_FOUND,"Conta não encontrada."));
    }
    private ResponseStatusException tokenInvalido() {
        return new ResponseStatusException(HttpStatus.BAD_REQUEST,"Link de recuperação inválido ou expirado.");
    }
    private String hash(String token) {
        try { return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(token.getBytes(StandardCharsets.UTF_8))); }
        catch (NoSuchAlgorithmException e) { throw new IllegalStateException("SHA-256 indisponível."); }
    }
}
