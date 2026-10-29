package com.gestao;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.gestao.domain.*;
import com.gestao.dto.CheckinResponseDTO;
import com.gestao.repository.*;
import com.gestao.security.TokenService;
import com.gestao.service.AgendaService;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.SignatureAlgorithm;
import io.jsonwebtoken.security.Keys;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.server.ResponseStatusException;

import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.*;

import static org.assertj.core.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class AquariusApiTests {
    @Autowired MockMvc mvc;
    @Autowired ObjectMapper mapper;
    @Autowired UsuarioRepository usuarios;
    @Autowired AulaRepository aulas;
    @Autowired CheckinRepository checkins;
    @Autowired PasswordEncoder encoder;
    @Autowired TokenService tokens;
    @Autowired AgendaService agenda;
    Usuario professor, aluno, outroAluno;
    Aula aula;

    @BeforeEach
    void setup() {
        checkins.deleteAll(); aulas.deleteAll(); usuarios.deleteAll();
        professor=usuario("professor@teste.com", Role.PROFESSOR);
        aluno=usuario("aluno@teste.com", Role.ALUNO);
        outroAluno=usuario("outro@teste.com", Role.ALUNO);
        aula=new Aula(); aula.setTitulo("Acrobacia"); aula.setProfessor(professor);
        aula.setDataHora(LocalDateTime.now().plusDays(2)); aula.setLimiteAlunos(1);
        aula=aulas.saveAndFlush(aula);
    }

    private Usuario usuario(String email, Role role) {
        var u=new Usuario(); u.setNome("Pessoa de teste"); u.setEmail(email);
        u.setSenha(encoder.encode("SenhaDeTeste123")); u.setRole(role);
        return usuarios.saveAndFlush(u);
    }
    private String bearer(Usuario usuario) { return "Bearer " + tokens.gerarToken(usuario); }
    private String body(Object data) throws Exception { return mapper.writeValueAsString(data); }

    @Test void cadastroPublicoNaoCriaProfessor() throws Exception {
        mvc.perform(post("/api/auth/cadastrar").contentType(MediaType.APPLICATION_JSON)
            .content(body(Map.of("nome","Invasor", "email","novo@teste.com", "senha","Senha123456", "role","PROFESSOR"))))
            .andExpect(status().isBadRequest());
        assertThat(usuarios.findByEmail("novo@teste.com")).isEmpty();
    }
    @Test void cadastroSemRoleCriaAlunoComHashProtegido() throws Exception {
        mvc.perform(post("/api/auth/cadastrar").contentType(MediaType.APPLICATION_JSON)
            .content(body(Map.of("nome","Novo", "email","NOVO@teste.com", "senha","Senha123456"))))
            .andExpect(status().isCreated()).andExpect(jsonPath("$.role").value("ALUNO"))
            .andExpect(jsonPath("$.senha").doesNotExist()).andExpect(jsonPath("$.password").doesNotExist());
        var u=usuarios.findByEmail("novo@teste.com").orElseThrow();
        assertThat(encoder.matches("Senha123456", u.getSenha())).isTrue();
    }
    @Test void emailDuplicadoIgnoraMaiusculas() throws Exception {
        mvc.perform(post("/api/auth/cadastrar").contentType(MediaType.APPLICATION_JSON)
            .content(body(Map.of("nome","Outro", "email","ALUNO@teste.com", "senha","Senha123456"))))
            .andExpect(status().isConflict());
    }
    @Test void validaCadastroIncompleto() throws Exception {
        mvc.perform(post("/api/auth/cadastrar").contentType(MediaType.APPLICATION_JSON).content("{}"))
            .andExpect(status().isBadRequest()).andExpect(jsonPath("$.campos.email").exists());
    }
    @Test void loginRetornaJsonETokenValido() throws Exception {
        var result=mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
            .content(body(Map.of("email","ALUNO@teste.com", "senha","SenhaDeTeste123"))))
            .andExpect(status().isOk()).andExpect(jsonPath("$.tipo").value("Bearer"))
            .andExpect(jsonPath("$.usuario.role").value("ALUNO")).andReturn();
        String token=mapper.readTree(result.getResponse().getContentAsString()).get("token").asText();
        assertThat(tokens.validarTokenPegandoEmail(token)).isEqualTo(aluno.getEmail());
    }
    @Test void senhaIncorretaRecebe401() throws Exception {
        mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
            .content(body(Map.of("email",aluno.getEmail(), "senha","errada"))))
            .andExpect(status().isUnauthorized()).andExpect(jsonPath("$.status").value(401));
    }
    @Test void autenticacaoObrigatoria() throws Exception {
        mvc.perform(get("/api/aluno/aulas")).andExpect(status().isUnauthorized());
    }
    @Test void alunoAcessaAgendaSemDadosPessoaisDoProfessor() throws Exception {
        mvc.perform(get("/api/aluno/aulas").header("Authorization",bearer(aluno)))
            .andExpect(status().isOk()).andExpect(jsonPath("$[0].vagasDisponiveis").value(1))
            .andExpect(jsonPath("$[0].professorNome").exists()).andExpect(jsonPath("$[0].professor").doesNotExist());
        String json=mapper.writeValueAsString(professor);
        assertThat(json).doesNotContain("\"senha\"", "\"password\"", professor.getSenha());
    }
    @Test void alunoNaoAcessaProfessor() throws Exception {
        mvc.perform(get("/api/professor/aulas").header("Authorization",bearer(aluno)))
            .andExpect(status().isForbidden());
    }
    @Test void professorVeSomenteSuasAulas() throws Exception {
        var p2=usuario("prof2@teste.com",Role.PROFESSOR);
        mvc.perform(get("/api/professor/aulas").header("Authorization",bearer(p2)))
            .andExpect(status().isOk()).andExpect(content().json("[]"));
        mvc.perform(get("/api/professor/aulas").header("Authorization",bearer(professor)))
            .andExpect(status().isOk()).andExpect(jsonPath("$.length()").value(1));
    }
    @Test void tokenInvalidoRecebe401() throws Exception {
        mvc.perform(get("/api/aluno/aulas").header("Authorization","Bearer invalido"))
            .andExpect(status().isUnauthorized()).andExpect(jsonPath("$.status").value(401));
    }
    @Test void tokenExpiradoRecebe401() throws Exception {
        String token=Jwts.builder().setIssuer("Aquarius Circus API").setSubject(aluno.getEmail())
            .setExpiration(new Date(System.currentTimeMillis()-60000))
            .signWith(Keys.hmacShaKeyFor("chave-exclusiva-de-testes-nao-usar-em-producao-123456".getBytes(StandardCharsets.UTF_8)),
                SignatureAlgorithm.HS256).compact();
        mvc.perform(get("/api/aluno/aulas").header("Authorization","Bearer " + token))
            .andExpect(status().isUnauthorized());
    }
    @Test void professorNaoPodeCriarAulaInvalida() throws Exception {
        mvc.perform(post("/api/professor/aulas").header("Authorization",bearer(professor))
            .contentType(MediaType.APPLICATION_JSON).content(body(Map.of("titulo","", "limiteAlunos",0,
                "dataHora",LocalDateTime.now().minusDays(1).toString()))))
            .andExpect(status().isBadRequest());
    }
    @Test void checkinExigeId() throws Exception {
        mvc.perform(post("/api/aluno/checkin").header("Authorization",bearer(aluno))
            .contentType(MediaType.APPLICATION_JSON).content("{}"))
            .andExpect(status().isBadRequest());
    }
    @Test void aulaInexistenteRecebe404() throws Exception {
        mvc.perform(post("/api/aluno/checkin").header("Authorization",bearer(aluno))
            .contentType(MediaType.APPLICATION_JSON).content(body(Map.of("aulaId",UUID.randomUUID()))))
            .andExpect(status().isNotFound());
    }
    @Test void aulaPassadaNaoAceitaReservaENaoApareceNaAgenda() throws Exception {
        aula.setDataHora(LocalDateTime.now().minusDays(1)); aulas.saveAndFlush(aula);
        assertThatThrownBy(() -> agenda.reservar(aluno,aula.getId())).isInstanceOf(ResponseStatusException.class);
        mvc.perform(get("/api/aluno/aulas").header("Authorization",bearer(aluno)))
            .andExpect(status().isOk()).andExpect(content().json("[]"));
    }
    @Test void cancelamentoLiberaVagaENaoPermiteCancelarReservaAlheia() throws Exception {
        var reserva=agenda.reservar(aluno,aula.getId());
        mvc.perform(patch("/api/aluno/checkins/{id}/cancelar",reserva.id()).header("Authorization",bearer(outroAluno)))
            .andExpect(status().isNotFound());
        mvc.perform(patch("/api/aluno/checkins/{id}/cancelar",reserva.id()).header("Authorization",bearer(aluno)))
            .andExpect(status().isOk()).andExpect(jsonPath("$.status").value("CANCELADO"));
        assertThat(agenda.reservar(outroAluno,aula.getId()).status()).isEqualTo("CONFIRMADO");
    }
    @Test void podeReservarNovamenteAposCancelarSemDuplicarRegistro() {
        var reserva=agenda.reservar(aluno,aula.getId());
        agenda.cancelar(aluno,reserva.id());
        assertThat(agenda.reservar(aluno,aula.getId()).id()).isEqualTo(reserva.id());
        assertThat(checkins.count()).isEqualTo(1);
    }
    @Test void minhasReservasNaoIncluemOutroAluno() throws Exception {
        agenda.reservar(aluno,aula.getId());
        mvc.perform(get("/api/aluno/checkins").header("Authorization",bearer(outroAluno)))
            .andExpect(status().isOk()).andExpect(content().json("[]"));
        mvc.perform(get("/api/aluno/checkins").header("Authorization",bearer(aluno)))
            .andExpect(status().isOk()).andExpect(jsonPath("$.length()").value(1));
    }
    @Test void corsAceitaOrigemLocalConfigurada() throws Exception {
        mvc.perform(options("/api/aluno/aulas").header("Origin","http://localhost:5173")
            .header("Access-Control-Request-Method","GET").header("Access-Control-Request-Headers","Authorization"))
            .andExpect(status().isOk()).andExpect(header().string("Access-Control-Allow-Origin","http://localhost:5173"));
    }
    @Test void duasReservasConcorrentesNaoUltrapassamUltimaVaga() throws Exception {
        concorrentes(aluno,outroAluno);
        assertThat(checkins.countByAulaAndStatusNot(aula,"CANCELADO")).isEqualTo(1);
    }
    @Test void mesmoAlunoConcorrenteNaoDuplicaReserva() throws Exception {
        aula.setLimiteAlunos(10); aulas.saveAndFlush(aula);
        concorrentes(aluno,aluno);
        assertThat(checkins.count()).isEqualTo(1);
    }
    private void concorrentes(Usuario a, Usuario b) throws Exception {
        var executor=Executors.newFixedThreadPool(2);
        var ready=new CountDownLatch(2); var start=new CountDownLatch(1);
        try {
            Callable<Boolean> t1=() -> reservarConcorrente(a,ready,start);
            Callable<Boolean> t2=() -> reservarConcorrente(b,ready,start);
            var f1=executor.submit(t1); var f2=executor.submit(t2);
            assertThat(ready.await(5,TimeUnit.SECONDS)).isTrue(); start.countDown();
            assertThat(List.of(f1.get(15,TimeUnit.SECONDS),f2.get(15,TimeUnit.SECONDS)))
                .containsExactlyInAnyOrder(true,false);
        } finally { start.countDown(); executor.shutdownNow(); }
    }
    private boolean reservarConcorrente(Usuario aluno, CountDownLatch ready, CountDownLatch start) throws Exception {
        ready.countDown(); if(!start.await(5,TimeUnit.SECONDS)) throw new IllegalStateException("timeout");
        try { agenda.reservar(aluno,aula.getId()); return true; }
        catch(ResponseStatusException e) { if(e.getStatusCode().value()!=409) throw e; return false; }
    }
}
