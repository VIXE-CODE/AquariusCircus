package com.gestao;

import com.gestao.domain.*;
import com.gestao.dto.*;
import com.gestao.repository.*;
import com.gestao.service.*;
import com.gestao.security.TokenService;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.server.ResponseStatusException;
import java.math.BigDecimal;
import java.time.*;
import java.util.*;
import java.util.concurrent.*;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class RegrasComerciaisTests {
    @Autowired MockMvc mvc;
    @Autowired UsuarioRepository usuarios;
    @Autowired AulaRepository aulas;
    @Autowired CheckinRepository checkins;
    @Autowired ContratoRepository contratos;
    @Autowired PagamentoRepository pagamentos;
    @Autowired PlanoRepository planos;
    @Autowired ConfiguracaoEstudioRepository configs;
    @Autowired PlanoFinanceiroService financeiro;
    @Autowired ConfiguracaoService configuracoes;
    @Autowired AgendaService agenda;
    @Autowired RecuperacaoSenhaRepository recuperacoes;
    @Autowired TokenService tokens;
    @MockBean Clock clock;
    Usuario professor, aluno;
    LocalDateTime agora;

    @BeforeEach void setup() {
        recuperacoes.deleteAll();
        checkins.deleteAll(); pagamentos.deleteAll(); contratos.deleteAll(); planos.deleteAll();
        aulas.deleteAll(); usuarios.deleteAll(); configs.deleteAll();
        agora=LocalDateTime.of(2026,11,5,9,0);
        horario(agora);
        professor=usuario("professor@teste.com",Role.PROFESSOR);
        aluno=usuario("aluno@teste.com",Role.ALUNO);
    }
    void horario(LocalDateTime hora) {
        when(clock.getZone()).thenReturn(ZoneId.of("America/Sao_Paulo"));
        when(clock.instant()).thenReturn(hora.atZone(ZoneId.of("America/Sao_Paulo")).toInstant());
    }
    Usuario usuario(String email, Role role) {
        var u=new Usuario(); u.setNome("Teste"); u.setEmail(email); u.setSenha("hash-fixture"); u.setRole(role);
        return usuarios.saveAndFlush(u);
    }
    ContratoResponseDTO contratar(int aulasMes, int meses) {
        var plano=financeiro.criarPlano(new PlanoRequestDTO("Pacote",aulasMes,meses,new BigDecimal("150.50")));
        return financeiro.contratar(professor,new ContratoRequestDTO(aluno.getId(),plano.id(),null));
    }
    ContratoResponseDTO ativar(int aulasMes,int meses) {
        var c=contratar(aulasMes,meses);
        return financeiro.primeiroPagamento(professor,c.id(),new PagamentoRequestDTO(agora.toLocalDate()));
    }
    Aula aula(LocalDateTime data) {
        var a=new Aula(); a.setTitulo("Circo"); a.setProfessor(professor); a.setDataHora(data); a.setLimiteAlunos(8);
        return aulas.saveAndFlush(a);
    }
    String bearer(Usuario u) { return "Bearer "+tokens.gerarToken(u); }
    void conflito(Runnable tarefa, String trecho) {
        assertThatThrownBy(tarefa::run).isInstanceOfSatisfying(ResponseStatusException.class,e -> {
            assertThat(e.getStatusCode().value()).isEqualTo(409); assertThat(e.getReason()).contains(trecho);
        });
    }
    @Test void mensalLiberaQuatroEVenceEmUmMes() {
        var c=ativar(4,1);
        assertThat(c.creditosDisponiveis()).isEqualTo(4);
        assertThat(c.dataInicio()).isEqualTo(LocalDate.of(2026,11,5));
        assertThat(c.dataVencimento()).isEqualTo(LocalDate.of(2026,12,5));
        assertThat(c.parcelas()).hasSize(1);
        assertThat(c.parcelas().get(0).valorPago()).isEqualByComparingTo("150.50");
    }
    @Test void todosOsSeisPacotesCalculamCreditosCorretamente() {
        for(int meses:List.of(1,3)) for(int quantidade:List.of(4,8,12)) {
            var p=financeiro.criarPlano(new PlanoRequestDTO("Pacote",quantidade,meses,new BigDecimal("100")));
            assertThat(p.aulasTotais()).isEqualTo(quantidade*meses);
        }
    }
    @Test void trimestralLiberaDozeDeUmaVezEParcelasMensais() {
        var c=ativar(4,3);
        assertThat(c.creditosDisponiveis()).isEqualTo(12);
        assertThat(c.dataVencimento()).isEqualTo(LocalDate.of(2027,2,5));
        assertThat(c.parcelas()).extracting(PagamentoResponseDTO::dataVencimento)
            .containsExactly(LocalDate.of(2026,11,5),LocalDate.of(2026,12,5),LocalDate.of(2027,1,5));
        assertThat(c.parcelas()).extracting(PagamentoResponseDTO::status).containsExactly("PAGO","PENDENTE","PENDENTE");
    }
    @Test void quatroAulasPodemSerUsadasNaMesmaSemana() {
        ativar(4,1);
        for(int i=1;i<=4;i++) agenda.reservar(aluno,aula(agora.plusDays(i)).getId());
        assertThat(financeiro.doAluno(aluno).get(0).creditosDisponiveis()).isZero();
        conflito(() -> agenda.reservar(aluno,aula(agora.plusDays(5)).getId()),"Saldo");
    }
    @Test void podeUsarAsDozeAulasNoPrimeiroMes() {
        ativar(4,3);
        for(int i=1;i<=12;i++) agenda.reservar(aluno,aula(agora.plusDays(i)).getId());
        assertThat(financeiro.doAluno(aluno).get(0).creditosConsumidos()).isEqualTo(12);
    }
    @Test void naoPodeReservarSemPlano() {
        var a=aula(agora.plusDays(1));
        conflito(() -> agenda.reservar(aluno,a.getId()),"plano ativo");
    }
    @Test void contratarNaoLiberaSaldoAntesDoPrimeiroPagamento() {
        var c=contratar(4,1);
        assertThat(c.situacao()).isEqualTo("AGUARDANDO_PAGAMENTO");
        assertThat(c.creditosDisponiveis()).isZero();
        conflito(() -> agenda.reservar(aluno,aula(agora.plusDays(1)).getId()),"plano ativo");
    }
    @Test void aulaNoDiaDoVencimentoNaoCabeNoPlano() {
        ativar(4,1);
        conflito(() -> agenda.reservar(aluno,aula(LocalDateTime.of(2026,12,5,10,0)).getId()),"validade");
    }
    @Test void creditoSobraExpiraENaoPassaParaRenovacao() {
        ativar(4,1); horario(agora.plusMonths(1));
        assertThat(financeiro.doAluno(aluno).get(0).creditosDisponiveis()).isZero();
        assertThat(financeiro.doAluno(aluno).get(0).situacao()).isEqualTo("VENCIDO");
        var novo=contratar(4,1);
        var renovado=financeiro.primeiroPagamento(professor,novo.id(),new PagamentoRequestDTO(LocalDate.of(2026,12,5)));
        assertThat(renovado.creditosDisponiveis()).isEqualTo(4);
    }
    @Test void parcelaVencidaBloqueiaEAConfirmacaoDesbloqueia() {
        var c=ativar(4,3); horario(agora.plusMonths(1));
        var a=aula(agora.plusMonths(1).plusDays(1));
        conflito(() -> agenda.reservar(aluno,a.getId()),"parcela");
        assertThat(financeiro.doAluno(aluno).get(0).situacao()).isEqualTo("INADIMPLENTE");
        financeiro.confirmarPagamento(professor,c.parcelas().get(1).id(),new PagamentoRequestDTO(LocalDate.of(2026,12,5)));
        assertThat(agenda.reservar(aluno,a.getId()).status()).isEqualTo("CONFIRMADO");
        assertThat(financeiro.doAluno(aluno).get(0).creditosDisponiveis()).isEqualTo(11);
    }
    @Test void parcelasFuturasNaoBloqueiamEAReservaPodeSerParaTerceiroMes() {
        ativar(4,3);
        assertThat(agenda.reservar(aluno,aula(agora.plusMonths(2)).getId()).status()).isEqualTo("CONFIRMADO");
    }
    @Test void fimDeMesNaoDeslocaVencimentosDasProximasParcelas() {
        agora=LocalDateTime.of(2026,1,31,9,0); horario(agora);
        var c=ativar(4,3);
        assertThat(c.dataVencimento()).isEqualTo(LocalDate.of(2026,4,30));
        assertThat(c.parcelas()).extracting(PagamentoResponseDTO::dataVencimento)
            .containsExactly(LocalDate.of(2026,1,31),LocalDate.of(2026,2,28),LocalDate.of(2026,3,31));
    }
    @Test void primeiroPagamentoDefineDataDeInicioEmVezDaCriacaoDoContrato() {
        var c=contratar(4,1); horario(agora.plusDays(5));
        var pago=financeiro.primeiroPagamento(professor,c.id(),new PagamentoRequestDTO(LocalDate.of(2026,11,10)));
        assertThat(pago.dataInicio()).isEqualTo(LocalDate.of(2026,11,10));
        assertThat(pago.dataVencimento()).isEqualTo(LocalDate.of(2026,12,10));
    }
    @Test void dividaDeContratoAnteriorTambemBloqueiaNovoPacote() {
        ativar(4,3); horario(agora.plusMonths(3));
        var novo=contratar(4,1);
        financeiro.primeiroPagamento(professor,novo.id(),new PagamentoRequestDTO(LocalDate.of(2027,2,5)));
        conflito(() -> agenda.reservar(aluno,aula(agora.plusMonths(3).plusDays(1)).getId()),"parcela");
    }
    @Test void alunoNaoVeContratosDeOutraConta() throws Exception {
        ativar(4,1); var outro=usuario("outroaluno@teste.com",Role.ALUNO);
        mvc.perform(get("/api/aluno/contratos").header("Authorization",bearer(outro)))
            .andExpect(status().isOk()).andExpect(content().json("[]"));
        mvc.perform(get("/api/aluno/contratos").header("Authorization",bearer(aluno)))
            .andExpect(status().isOk()).andExpect(jsonPath("$[0].creditosDisponiveis").value(4))
            .andExpect(jsonPath("$[0].parcelas[0].status").value("PAGO"));
    }
    @Test void planoComDuracaoOuQuantidadeInvalidaERejeitado() throws Exception {
        mvc.perform(post("/api/professor/planos").header("Authorization",bearer(professor))
            .contentType(MediaType.APPLICATION_JSON)
            .content("{\"nome\":\"Inválido\",\"aulasPorMes\":5,\"duracaoMeses\":2,\"valorMensal\":100}"))
            .andExpect(status().isBadRequest());
    }
    @Test void pagamentoFuturoERejeitadoSemAtivarContrato() throws Exception {
        var c=contratar(4,1);
        mvc.perform(post("/api/professor/contratos/{id}/primeiro-pagamento",c.id()).header("Authorization",bearer(professor))
            .contentType(MediaType.APPLICATION_JSON).content("{\"dataPagamento\":\"2026-11-06\"}"))
            .andExpect(status().isBadRequest());
        assertThat(pagamentos.count()).isZero();
        assertThat(financeiro.doAluno(aluno).get(0).situacao()).isEqualTo("AGUARDANDO_PAGAMENTO");
    }
    @Test void professorConsultaParticipantesDaPropriaAulaSemDadosSensiveis() throws Exception {
        ativar(4,1); var a=aula(agora.plusDays(1)); agenda.reservar(aluno,a.getId());
        mvc.perform(get("/api/professor/aulas/{id}/checkins",a.getId()).header("Authorization",bearer(professor)))
            .andExpect(status().isOk()).andExpect(jsonPath("$[0].alunoNome").value("Teste"))
            .andExpect(jsonPath("$[0].senha").doesNotExist()).andExpect(jsonPath("$[0].email").doesNotExist());
        var outro=usuario("prof2@teste.com",Role.PROFESSOR);
        mvc.perform(get("/api/professor/aulas/{id}/checkins",a.getId()).header("Authorization",bearer(outro)))
            .andExpect(status().isNotFound());
    }
    @Test void cancelamentoExatamenteNoLimiteDevolveCredito() {
        ativar(4,1); var a=aula(agora.plusHours(12));
        var r=agenda.reservar(aluno,a.getId());
        assertThat(agenda.cancelar(aluno,r.id()).status()).isEqualTo("CANCELADO");
        assertThat(financeiro.doAluno(aluno).get(0).creditosDisponiveis()).isEqualTo(4);
    }
    @Test void umSegundoAposPrazoMarcaFaltaSemDevolverCreditoELiberaVaga() {
        ativar(4,1); var a=aula(agora.plusHours(12));
        var r=agenda.reservar(aluno,a.getId()); horario(agora.plusSeconds(1));
        assertThat(agenda.cancelar(aluno,r.id()).status()).isEqualTo("FALTA");
        assertThat(financeiro.doAluno(aluno).get(0).creditosDisponiveis()).isEqualTo(3);
        assertThat(agenda.disponiveis().get(0).vagasDisponiveis()).isEqualTo(8);
        assertThat(agenda.cancelar(aluno,r.id()).status()).isEqualTo("FALTA");
        conflito(() -> agenda.reservar(aluno,a.getId()),"já possui");
    }
    @Test void prazoPodeSerAlteradoESobreviveAOutraConsulta() {
        assertThat(configuracoes.consultar().antecedenciaCancelamentoHoras()).isEqualTo(12);
        configuracoes.atualizar(new ConfiguracaoRequestDTO(24));
        assertThat(configs.findById(1L).orElseThrow().getAntecedenciaCancelamentoHoras()).isEqualTo(24);
        ativar(4,1); var r=agenda.reservar(aluno,aula(agora.plusHours(20)).getId());
        assertThat(agenda.cancelar(aluno,r.id()).status()).isEqualTo("FALTA");
    }
    @Test void alunoNaoMudaConfiguracaoMasProfessorPode() throws Exception {
        mvc.perform(patch("/api/professor/configuracoes").header("Authorization",bearer(aluno))
            .contentType(MediaType.APPLICATION_JSON).content("{\"antecedenciaCancelamentoHoras\":24}"))
            .andExpect(status().isForbidden());
        mvc.perform(patch("/api/professor/configuracoes").header("Authorization",bearer(professor))
            .contentType(MediaType.APPLICATION_JSON).content("{\"antecedenciaCancelamentoHoras\":24}"))
            .andExpect(status().isOk()).andExpect(jsonPath("$.antecedenciaCancelamentoHoras").value(24));
        mvc.perform(get("/api/configuracoes").header("Authorization",bearer(aluno)))
            .andExpect(status().isOk()).andExpect(jsonPath("$.antecedenciaCancelamentoHoras").value(24));
    }
    @Test void configuracaoNegativaOuNulaERejeitada() throws Exception {
        for(String json:List.of("{}","{\"antecedenciaCancelamentoHoras\":-1}"))
            mvc.perform(patch("/api/professor/configuracoes").header("Authorization",bearer(professor))
                .contentType(MediaType.APPLICATION_JSON).content(json)).andExpect(status().isBadRequest());
    }
    @Test void criacaoDeAulaTemOitoVagasENaoAceitaOutroLimite() throws Exception {
        var a=agenda.criar(professor,new AulaRequestDTO("Aéreo",agora.plusDays(1),null));
        assertThat(a.limiteAlunos()).isEqualTo(8);
        mvc.perform(post("/api/professor/aulas").header("Authorization",bearer(professor))
            .contentType(MediaType.APPLICATION_JSON)
            .content("{\"titulo\":\"Circo\",\"dataHora\":\"2026-11-06T19:00:00\",\"limiteAlunos\":9}"))
            .andExpect(status().isBadRequest());
    }
    @Test void alunoNaoPodeConfirmarPagamentoENemCriarPlano() throws Exception {
        mvc.perform(post("/api/professor/planos").header("Authorization",bearer(aluno))
            .contentType(MediaType.APPLICATION_JSON).content("{}")) .andExpect(status().isForbidden());
        mvc.perform(post("/api/professor/pagamentos/{id}/confirmar",UUID.randomUUID()).header("Authorization",bearer(aluno))
            .contentType(MediaType.APPLICATION_JSON).content("{\"dataPagamento\":\"2026-11-05\"}"))
            .andExpect(status().isForbidden());
    }
    @Test void professorNaoConfirmaContratoDeOutroProfessor() {
        var c=contratar(4,1); var outro=usuario("outroprof@teste.com",Role.PROFESSOR);
        assertThatThrownBy(() -> financeiro.primeiroPagamento(outro,c.id(),new PagamentoRequestDTO(agora.toLocalDate())))
            .isInstanceOfSatisfying(ResponseStatusException.class,e -> assertThat(e.getStatusCode().value()).isEqualTo(404));
    }
    @Test void confirmarPrimeiroPagamentoDuasVezesNaoDuplicaCreditosOuParcelas() {
        var c=ativar(4,3);
        conflito(() -> financeiro.primeiroPagamento(professor,c.id(),new PagamentoRequestDTO(agora.toLocalDate())),"já registrado");
        assertThat(pagamentos.count()).isEqualTo(3);
        assertThat(financeiro.doAluno(aluno).get(0).creditosDisponiveis()).isEqualTo(12);
    }
    @Test void confirmarParcelaNovamenteNaoGeraNovoCredito() {
        var c=ativar(4,3);
        financeiro.confirmarPagamento(professor,c.parcelas().get(1).id(),new PagamentoRequestDTO(agora.toLocalDate()));
        financeiro.confirmarPagamento(professor,c.parcelas().get(1).id(),new PagamentoRequestDTO(agora.toLocalDate()));
        assertThat(pagamentos.count()).isEqualTo(3);
        assertThat(financeiro.doAluno(aluno).get(0).creditosDisponiveis()).isEqualTo(12);
    }
    @Test void naoPodeTerDoisContratosAtivosOuPendentes() {
        contratar(4,1); conflito(() -> contratar(8,1),"já possui contrato");
    }
    @Test void cancelamentoDepoisDoInicioTambemContaFalta() {
        ativar(4,1); var r=agenda.reservar(aluno,aula(agora.plusHours(1)).getId());
        horario(agora.plusHours(2));
        assertThat(agenda.cancelar(aluno,r.id()).status()).isEqualTo("FALTA");
    }
    @Test void presencaSomentePeloProfessorDaAulaEAposInicio() {
        ativar(4,1); var r=agenda.reservar(aluno,aula(agora.plusHours(1)).getId());
        conflito(() -> agenda.registrarPresenca(professor,r.id(),new PresencaRequestDTO("PRESENTE")),"não começou");
        horario(agora.plusHours(2));
        var outro=usuario("prof2@teste.com",Role.PROFESSOR);
        assertThatThrownBy(() -> agenda.registrarPresenca(outro,r.id(),new PresencaRequestDTO("FALTA")))
            .isInstanceOfSatisfying(ResponseStatusException.class,e -> assertThat(e.getStatusCode().value()).isEqualTo(404));
        assertThat(agenda.registrarPresenca(professor,r.id(),new PresencaRequestDTO("PRESENTE")).status()).isEqualTo("PRESENTE");
        assertThat(financeiro.doAluno(aluno).get(0).creditosDisponiveis()).isEqualTo(3);
    }
    @Test void ultimoCreditoNaoPodeSerGastoSimultaneamenteEmAulasDiferentes() throws Exception {
        ativar(4,1);
        for(int i=1;i<=3;i++) agenda.reservar(aluno,aula(agora.plusDays(i)).getId());
        var a=aula(agora.plusDays(4)); var b=aula(agora.plusDays(5));
        var pool=Executors.newFixedThreadPool(2); var ready=new CountDownLatch(2); var start=new CountDownLatch(1);
        try {
            var f1=pool.submit(() -> reservarConcorrente(a,ready,start));
            var f2=pool.submit(() -> reservarConcorrente(b,ready,start));
            assertThat(ready.await(5,TimeUnit.SECONDS)).isTrue(); start.countDown();
            assertThat(List.of(f1.get(15,TimeUnit.SECONDS),f2.get(15,TimeUnit.SECONDS)))
                .containsExactlyInAnyOrder(true,false);
            assertThat(financeiro.doAluno(aluno).get(0).creditosConsumidos()).isEqualTo(4);
        } finally { start.countDown(); pool.shutdownNow(); }
    }
    boolean reservarConcorrente(Aula a, CountDownLatch ready,CountDownLatch start) throws Exception {
        ready.countDown(); if(!start.await(5,TimeUnit.SECONDS)) throw new IllegalStateException("timeout");
        try { agenda.reservar(aluno,a.getId()); return true; }
        catch(ResponseStatusException e) { if(e.getStatusCode().value()!=409) throw e; return false; }
    }
}
