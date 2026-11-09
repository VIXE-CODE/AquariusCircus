package com.gestao;

import com.gestao.domain.*;
import com.gestao.dto.*;
import com.gestao.repository.*;
import com.gestao.security.TokenService;
import com.gestao.service.*;
import org.junit.jupiter.api.*;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.mail.*;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.server.ResponseStatusException;
import java.math.BigDecimal;
import java.time.*;
import java.util.*;
import java.util.concurrent.*;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(properties={"spring.mail.host=localhost","app.recuperacao.remetente=teste@aquarius.test"})
@AutoConfigureMockMvc
@ActiveProfiles("test")
class AulasEContasTests {
    @Autowired MockMvc mvc;
    @Autowired UsuarioRepository usuarios;
    @Autowired AulaRepository aulas;
    @Autowired CheckinRepository checkins;
    @Autowired ContratoRepository contratos;
    @Autowired PagamentoRepository pagamentos;
    @Autowired PlanoRepository planos;
    @Autowired RecuperacaoSenhaRepository recuperacoes;
    @Autowired ConfiguracaoEstudioRepository configs;
    @Autowired AgendaService agenda;
    @Autowired PlanoFinanceiroService financeiro;
    @Autowired SenhaService senhas;
    @Autowired TokenService tokens;
    @Autowired PasswordEncoder encoder;
    @MockBean Clock clock;
    @MockBean JavaMailSender mail;
    Usuario professor,outroProfessor,aluno;
    Aula aula;
    LocalDateTime agora;

    @BeforeEach void setup() {
        recuperacoes.deleteAll();checkins.deleteAll();pagamentos.deleteAll();contratos.deleteAll();planos.deleteAll();
        aulas.deleteAll();usuarios.deleteAll();configs.deleteAll();reset(mail);
        agora=LocalDateTime.of(2026,11,5,9,0);horario(agora);
        professor=usuario("professor@aquarius.test",Role.PROFESSOR);
        outroProfessor=usuario("outroprofessor@aquarius.test",Role.PROFESSOR);
        aluno=usuario("aluno@aquarius.test",Role.ALUNO);
        var plano=financeiro.criarPlano(new PlanoRequestDTO("Mensal",4,1,new BigDecimal("100")));
        var c=financeiro.contratar(professor,new ContratoRequestDTO(aluno.getId(),plano.id(),null));
        financeiro.primeiroPagamento(professor,c.id(),new PagamentoRequestDTO(agora.toLocalDate()));
        aula=new Aula();aula.setTitulo("Aula original");aula.setDataHora(agora.plusDays(2));
        aula.setProfessor(professor);aula.setLimiteAlunos(8);aula=aulas.saveAndFlush(aula);
    }
    void horario(LocalDateTime data) {
        when(clock.getZone()).thenReturn(ZoneId.of("America/Sao_Paulo"));
        when(clock.instant()).thenReturn(data.atZone(ZoneId.of("America/Sao_Paulo")).toInstant());
    }
    Usuario usuario(String email,Role role) {
        var u=new Usuario();u.setNome("Teste");u.setEmail(email);u.setRole(role);
        u.setSenha(encoder.encode("SenhaTeste123!"));return usuarios.saveAndFlush(u);
    }
    String bearer(Usuario u) {return "Bearer "+tokens.gerarToken(u);}
    String solicitarToken() {
        senhas.solicitar(new RecuperacaoSenhaDTO(aluno.getEmail()));
        var capture=ArgumentCaptor.forClass(SimpleMailMessage.class);
        verify(mail,timeout(3000)).send(capture.capture());
        return capture.getValue().getText().split("token=")[1].split("\\s")[0];
    }
    @Test void professorEditaAulaSemPerderReservasOuCreditos() {
        var reserva=agenda.reservar(aluno,aula.getId());
        var editada=agenda.editar(professor,aula.getId(),new AulaEdicaoDTO("Novo título",agora.plusDays(3)));
        assertThat(editada.id()).isEqualTo(aula.getId());assertThat(editada.titulo()).isEqualTo("Novo título");
        var minhas=agenda.minhasReservas(aluno);
        assertThat(minhas.get(0).id()).isEqualTo(reserva.id());assertThat(minhas.get(0).dataHora()).isEqualTo(agora.plusDays(3));
        assertThat(financeiro.doAluno(aluno).get(0).creditosDisponiveis()).isEqualTo(3);
    }
    @Test void outroProfessorNaoEditaOuCancelaAula() throws Exception {
        mvc.perform(patch("/api/professor/aulas/{id}",aula.getId()).header("Authorization",bearer(outroProfessor))
            .contentType(MediaType.APPLICATION_JSON).content("{\"titulo\":\"Indevida\",\"dataHora\":\"2026-11-08T19:00:00\"}"))
            .andExpect(status().isNotFound());
        mvc.perform(patch("/api/professor/aulas/{id}/cancelar",aula.getId()).header("Authorization",bearer(outroProfessor)))
            .andExpect(status().isNotFound());
    }
    @Test void alunoNaoEditaOuCancelaAulaInteira() throws Exception {
        mvc.perform(patch("/api/professor/aulas/{id}",aula.getId()).header("Authorization",bearer(aluno))
            .contentType(MediaType.APPLICATION_JSON).content("{}")) .andExpect(status().isForbidden());
        mvc.perform(patch("/api/professor/aulas/{id}/cancelar",aula.getId()).header("Authorization",bearer(aluno)))
            .andExpect(status().isForbidden());
    }
    @Test void novoHorarioNaoPodeUltrapassarValidadeDoPlanoReservado() {
        agenda.reservar(aluno,aula.getId());
        assertThatThrownBy(()->agenda.editar(professor,aula.getId(),new AulaEdicaoDTO("Novo",agora.plusMonths(1))))
            .isInstanceOf(ResponseStatusException.class);
        assertThat(aulas.findById(aula.getId()).orElseThrow().getDataHora()).isEqualTo(agora.plusDays(2));
    }
    @Test void cancelamentoProfessorDevolveCreditoEPreservaHistorico() {
        var r=agenda.reservar(aluno,aula.getId());
        var cancelada=agenda.cancelarAula(professor,aula.getId(),new CancelamentoAulaDTO("Manutenção"));
        assertThat(cancelada.cancelada()).isTrue();assertThat(cancelada.motivoCancelamento()).isEqualTo("Manutenção");
        assertThat(cancelada.vagasDisponiveis()).isZero();assertThat(aulas.existsById(aula.getId())).isTrue();
        assertThat(agenda.disponiveis()).isEmpty();assertThat(agenda.doProfessor(professor)).hasSize(1);
        var reserva=agenda.minhasReservas(aluno).get(0);
        assertThat(reserva.id()).isEqualTo(r.id());assertThat(reserva.status()).isEqualTo("CANCELADO_AULA");
        assertThat(reserva.aulaCancelada()).isTrue();assertThat(financeiro.doAluno(aluno).get(0).creditosDisponiveis()).isEqualTo(4);
    }
    @Test void cancelamentoRepetidoNaoAcumulaCreditos() {
        var r=agenda.reservar(aluno,aula.getId());
        agenda.cancelarAula(professor,aula.getId(),null);agenda.cancelarAula(professor,aula.getId(),null);
        assertThat(agenda.cancelar(aluno,r.id()).status()).isEqualTo("CANCELADO_AULA");
        assertThat(financeiro.doAluno(aluno).get(0).creditosDisponiveis()).isEqualTo(4);
    }
    @Test void aulaCanceladaNaoAceitaReservaEdicaoOuPresenca() {
        var r=agenda.reservar(aluno,aula.getId());agenda.cancelarAula(professor,aula.getId(),null);
        assertThatThrownBy(()->agenda.reservar(aluno,aula.getId())).isInstanceOf(ResponseStatusException.class);
        assertThatThrownBy(()->agenda.editar(professor,aula.getId(),new AulaEdicaoDTO("Novo",agora.plusDays(3))))
            .isInstanceOf(ResponseStatusException.class);
        assertThatThrownBy(()->agenda.registrarPresenca(professor,r.id(),new PresencaRequestDTO("PRESENTE")))
            .isInstanceOf(ResponseStatusException.class);
    }
    @Test void professorNaoAlteraHistoricoDeAulaIniciada() {
        horario(agora.plusDays(3));
        assertThatThrownBy(()->agenda.cancelarAula(professor,aula.getId(),null)).isInstanceOf(ResponseStatusException.class);
        assertThatThrownBy(()->agenda.editar(professor,aula.getId(),new AulaEdicaoDTO("Novo",agora.plusDays(4))))
            .isInstanceOf(ResponseStatusException.class);
    }
    @Test void cancelamentoDaEscolaNaoProrrogaCreditoVencido() {
        agenda.reservar(aluno,aula.getId());
        var contrato=contratos.findByAlunoIdOrderByDataInicioDesc(aluno.getId()).get(0);
        contrato.setDataVencimento(agora.toLocalDate());contratos.saveAndFlush(contrato);
        agenda.cancelarAula(professor,aula.getId(),null);
        assertThat(financeiro.doAluno(aluno).get(0).creditosDisponiveis()).isZero();
    }
    @Test void faltaAntecipadaTambemEEstornadaSeEscolaCancela() {
        var r=agenda.reservar(aluno,aula.getId());horario(agora.plusDays(2).minusHours(1));
        assertThat(agenda.cancelar(aluno,r.id()).status()).isEqualTo("FALTA");
        agenda.cancelarAula(professor,aula.getId(),null);
        assertThat(financeiro.doAluno(aluno).get(0).creditosDisponiveis()).isEqualTo(4);
    }
    @Test void professorCriaOutroProfessorESenhaNaoSaiNaResposta() throws Exception {
        mvc.perform(post("/api/professor/professores").header("Authorization",bearer(professor))
            .contentType(MediaType.APPLICATION_JSON).content("{\"nome\":\"Novo\",\"email\":\"NOVO@aquarius.test\",\"senha\":\"SenhaNova123!\"}"))
            .andExpect(status().isCreated()).andExpect(jsonPath("$.role").value("PROFESSOR"))
            .andExpect(jsonPath("$.senha").doesNotExist()).andExpect(jsonPath("$.password").doesNotExist());
        var novo=usuarios.findByEmail("novo@aquarius.test").orElseThrow();
        assertThat(encoder.matches("SenhaNova123!",novo.getSenha())).isTrue();
    }
    @Test void criacaoProfessorExigeLoginDeProfessor() throws Exception {
        mvc.perform(post("/api/professor/professores").contentType(MediaType.APPLICATION_JSON).content("{}"))
            .andExpect(status().isUnauthorized());
        mvc.perform(post("/api/professor/professores").header("Authorization",bearer(aluno))
            .contentType(MediaType.APPLICATION_JSON).content("{}")) .andExpect(status().isForbidden());
    }
    @Test void mensagemRecuperacaoNaoRevelaSeEmailExiste() throws Exception {
        var conhecido=mvc.perform(post("/api/auth/recuperar-senha").contentType(MediaType.APPLICATION_JSON)
            .content("{\"email\":\"aluno@aquarius.test\"}")) .andExpect(status().isAccepted()).andReturn();
        var desconhecido=mvc.perform(post("/api/auth/recuperar-senha").contentType(MediaType.APPLICATION_JSON)
            .content("{\"email\":\"naoexiste@aquarius.test\"}")) .andExpect(status().isAccepted()).andReturn();
        assertThat(conhecido.getResponse().getContentAsString()).isEqualTo(desconhecido.getResponse().getContentAsString());
        verify(mail,timeout(3000)).send(any(SimpleMailMessage.class));
    }
    @Test void tokenEEnviadoPorEmailMasSomenteHashFicaNoBanco() {
        String token=solicitarToken();var registro=recuperacoes.findByUsuarioId(aluno.getId()).orElseThrow();
        assertThat(token).matches("[A-Za-z0-9_-]{43}");assertThat(registro.getTokenHash()).hasSize(64).isNotEqualTo(token);
        assertThat(registro.getExpiraEm()).isEqualTo(clock.instant().plusSeconds(900));
    }
    @Test void trocaSenhaRevogaJwtAntigoENaoPermiteReutilizarLink() throws Exception {
        String anterior=bearer(aluno);String token=solicitarToken();
        mvc.perform(post("/api/auth/redefinir-senha").contentType(MediaType.APPLICATION_JSON)
            .content("{\"token\":\""+token+"\",\"novaSenha\":\"OutraSenha123!\"}")) .andExpect(status().isOk());
        mvc.perform(get("/api/auth/me").header("Authorization",anterior)).andExpect(status().isUnauthorized());
        mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
            .content("{\"email\":\"aluno@aquarius.test\",\"senha\":\"SenhaTeste123!\"}")) .andExpect(status().isUnauthorized());
        mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
            .content("{\"email\":\"aluno@aquarius.test\",\"senha\":\"OutraSenha123!\"}")) .andExpect(status().isOk());
        assertThatThrownBy(()->senhas.redefinir(new RedefinirSenhaDTO(token,"OutraSenha456!")))
            .isInstanceOf(ResponseStatusException.class);
    }
    @Test void tokenExpiradoNoInstanteExatoNaoTrocaSenha() {
        String token=solicitarToken();horario(agora.plusMinutes(15));
        assertThatThrownBy(()->senhas.redefinir(new RedefinirSenhaDTO(token,"OutraSenha123!")))
            .isInstanceOf(ResponseStatusException.class);
        assertThat(encoder.matches("SenhaTeste123!",usuarios.findById(aluno.getId()).orElseThrow().getSenha())).isTrue();
    }
    @Test void pedidosRepetidosAntesDeUmMinutoNaoEnviamNovosLinks() {
        solicitarToken();senhas.solicitar(new RecuperacaoSenhaDTO(aluno.getEmail()));
        verify(mail,after(150).times(1)).send(any(SimpleMailMessage.class));
        assertThat(recuperacoes.count()).isEqualTo(1);
    }
    @Test void novoPedidoAposUmMinutoInvalidaLinkAnterior() {
        String antigo=solicitarToken();horario(agora.plusSeconds(60));clearInvocations(mail);
        String novo=solicitarToken();assertThat(novo).isNotEqualTo(antigo);
        assertThatThrownBy(()->senhas.redefinir(new RedefinirSenhaDTO(antigo,"OutraSenha123!")))
            .isInstanceOf(ResponseStatusException.class);
        senhas.redefinir(new RedefinirSenhaDTO(novo,"OutraSenha123!"));
    }
    @Test void tokenAleatorioENovaSenhaCurtaSaoRejeitados() throws Exception {
        mvc.perform(post("/api/auth/redefinir-senha").contentType(MediaType.APPLICATION_JSON)
            .content("{\"token\":\"invalido\",\"novaSenha\":\"curta\"}")) .andExpect(status().isBadRequest());
        assertThatThrownBy(()->senhas.redefinir(new RedefinirSenhaDTO("A".repeat(43),"OutraSenha123!")))
            .isInstanceOf(ResponseStatusException.class);
    }
    @Test void alterarSenhaExigeSenhaAtualERevogaLinksPendentes() throws Exception {
        String token=solicitarToken();String jwt=bearer(aluno);
        mvc.perform(patch("/api/auth/senha").header("Authorization",jwt).contentType(MediaType.APPLICATION_JSON)
            .content("{\"senhaAtual\":\"errada\",\"novaSenha\":\"OutraSenha123!\"}")) .andExpect(status().isBadRequest());
        mvc.perform(patch("/api/auth/senha").header("Authorization",jwt).contentType(MediaType.APPLICATION_JSON)
            .content("{\"senhaAtual\":\"SenhaTeste123!\",\"novaSenha\":\"OutraSenha123!\"}")) .andExpect(status().isOk());
        mvc.perform(get("/api/auth/me").header("Authorization",jwt)).andExpect(status().isUnauthorized());
        assertThatThrownBy(()->senhas.redefinir(new RedefinirSenhaDTO(token,"OutraSenha456!")))
            .isInstanceOf(ResponseStatusException.class);
    }
    @Test void falhaSmtpNaoRevelaContaNemTrocaSenha() throws Exception {
        doThrow(new MailSendException("Falha fictícia")).when(mail).send(any(SimpleMailMessage.class));
        mvc.perform(post("/api/auth/recuperar-senha").contentType(MediaType.APPLICATION_JSON)
            .content("{\"email\":\"aluno@aquarius.test\"}")) .andExpect(status().isAccepted());
        verify(mail,timeout(3000)).send(any(SimpleMailMessage.class));
        assertThat(encoder.matches("SenhaTeste123!",usuarios.findById(aluno.getId()).orElseThrow().getSenha())).isTrue();
    }
    @Test void semSmtpConfiguradoRetorna503SemGerarToken() {
        var indisponivel=mock(EmailRecuperacaoService.class);
        var eventos=mock(org.springframework.context.ApplicationEventPublisher.class);
        var service=new SenhaService(usuarios,recuperacoes,encoder,indisponivel,eventos,clock);
        for(String email:List.of(aluno.getEmail(),"desconhecido@aquarius.test"))
            assertThatThrownBy(()->service.solicitar(new RecuperacaoSenhaDTO(email)))
                .isInstanceOfSatisfying(ResponseStatusException.class,e->assertThat(e.getStatusCode().value()).isEqualTo(503));
        assertThat(recuperacoes.count()).isZero();verifyNoInteractions(eventos);
    }
    @Test void filaOcupadaNaoExibeDadosDoPedidoOuQuebraResposta() {
        @SuppressWarnings("unchecked")
        var provider=mock(org.springframework.beans.factory.ObjectProvider.class);
        java.util.concurrent.Executor lotado=task->{throw new RejectedExecutionException("fila fictícia");};
        var service=new EmailRecuperacaoService(provider,"localhost","teste@aquarius.test",
            "http://localhost:5173/redefinir-senha",lotado);
        assertThatCode(()->service.enviar(new RecuperacaoSolicitada(aluno.getEmail(),"segredo-ficticio"))).doesNotThrowAnyException();
        verifyNoInteractions(provider);
    }
    @Test void doisUsosConcorrentesDoLinkTemApenasUmSucesso() throws Exception {
        String token=solicitarToken();var pool=Executors.newFixedThreadPool(2);
        var ready=new CountDownLatch(2);var start=new CountDownLatch(1);
        try {
            Callable<Boolean> task=()->{ready.countDown();if(!start.await(5,TimeUnit.SECONDS))throw new IllegalStateException("timeout");
                try{senhas.redefinir(new RedefinirSenhaDTO(token,"OutraSenha123!"));return true;}
                catch(ResponseStatusException e){if(e.getStatusCode().value()!=400)throw e;return false;}};
            var a=pool.submit(task);var b=pool.submit(task);
            assertThat(ready.await(5,TimeUnit.SECONDS)).isTrue();start.countDown();
            assertThat(List.of(a.get(15,TimeUnit.SECONDS),b.get(15,TimeUnit.SECONDS)))
                .containsExactlyInAnyOrder(true,false);
            assertThat(usuarios.findById(aluno.getId()).orElseThrow().getSenhaVersao()).isEqualTo(1);
        }finally{start.countDown();pool.shutdownNow();}
    }
    @Test void reservaConcorrenteComCancelamentoDaAulaNaoDeixaCreditoPreso() throws Exception {
        var pool=Executors.newFixedThreadPool(2);var ready=new CountDownLatch(2);var start=new CountDownLatch(1);
        try {
            var reserva=pool.submit(()->{ready.countDown();start.await();try{agenda.reservar(aluno,aula.getId());}
                catch(ResponseStatusException e){if(e.getStatusCode().value()!=409)throw e;}return true;});
            var cancelamento=pool.submit(()->{ready.countDown();start.await();agenda.cancelarAula(professor,aula.getId(),null);return true;});
            assertThat(ready.await(5,TimeUnit.SECONDS)).isTrue();start.countDown();
            assertThat(reserva.get(15,TimeUnit.SECONDS)).isTrue();assertThat(cancelamento.get(15,TimeUnit.SECONDS)).isTrue();
            assertThat(aulas.findById(aula.getId()).orElseThrow().getCancelada()).isTrue();
            assertThat(financeiro.doAluno(aluno).get(0).creditosDisponiveis()).isEqualTo(4);
        }finally{start.countDown();pool.shutdownNow();}
    }
}
