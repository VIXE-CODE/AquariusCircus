package com.gestao.service;

import com.gestao.domain.*;
import com.gestao.dto.*;
import com.gestao.repository.*;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import java.time.*;
import java.util.*;

@Service
public class PlanoFinanceiroService {
    public static final List<String> CONSOMEM_CREDITO = List.of("CONFIRMADO", "PRESENTE", "FALTA");
    private final PlanoRepository planos;
    private final ContratoRepository contratos;
    private final PagamentoRepository pagamentos;
    private final UsuarioRepository usuarios;
    private final CheckinRepository checkins;
    private final Clock clock;

    public PlanoFinanceiroService(PlanoRepository planos, ContratoRepository contratos,
        PagamentoRepository pagamentos, UsuarioRepository usuarios, CheckinRepository checkins, Clock clock) {
        this.planos = planos; this.contratos = contratos; this.pagamentos = pagamentos;
        this.usuarios = usuarios; this.checkins = checkins; this.clock = clock;
    }
    @Transactional(readOnly = true)
    public List<PlanoResponseDTO> listarPlanos() {
        return planos.findAll().stream().map(PlanoResponseDTO::from).toList();
    }
    @Transactional
    public PlanoResponseDTO criarPlano(PlanoRequestDTO dto) {
        var plano = new Plano();
        plano.setNome(dto.nome().trim()); plano.setAulasPorMes(dto.aulasPorMes());
        plano.setDuracaoMeses(dto.duracaoMeses()); plano.setValorPadrao(dto.valorMensal());
        return PlanoResponseDTO.from(planos.saveAndFlush(plano));
    }
    // Contratação, pagamento e reservas usam a mesma trava, inclusive em aulas diferentes.
    public Usuario bloquearAluno(UUID id) {
        return usuarios.findComBloqueioById(id).orElseThrow(() -> erro(HttpStatus.NOT_FOUND, "Aluno não encontrado."));
    }
    @Transactional
    public ContratoResponseDTO contratar(Usuario professor, ContratoRequestDTO dto) {
        var aluno = bloquearAluno(dto.alunoId());
        if (aluno.getRole() != Role.ALUNO) throw erro(HttpStatus.BAD_REQUEST, "O contrato exige um aluno.");
        if (contratos.findByAlunoIdOrderByDataInicioDesc(aluno.getId()).stream()
                .anyMatch(c -> c.getDataInicio() == null || vigente(c)))
            throw erro(HttpStatus.CONFLICT, "O aluno já possui contrato vigente ou aguardando primeiro pagamento.");
        var plano = planos.findById(dto.planoId()).orElseThrow(() -> erro(HttpStatus.NOT_FOUND, "Plano não encontrado."));
        if (plano.getAulasPorMes() == null || plano.getDuracaoMeses() == null || plano.getValorPadrao() == null)
            throw erro(HttpStatus.CONFLICT, "Plano antigo precisa ser migrado antes da contratação.");
        var contrato = new Contrato();
        contrato.setAluno(aluno); contrato.setPlano(plano); contrato.setProfessor(professor);
        contrato.setDuracaoMeses(plano.getDuracaoMeses());
        contrato.setAulasContratadas(plano.getAulasPorMes() * plano.getDuracaoMeses());
        contrato.setValorAcordado(dto.valorMensal() == null ? plano.getValorPadrao() : dto.valorMensal());
        contrato.setAtivo(false); contrato.setContratoAceito(false);
        return resposta(contratos.saveAndFlush(contrato));
    }
    @Transactional
    public ContratoResponseDTO primeiroPagamento(Usuario professor, UUID id, PagamentoRequestDTO dto) {
        UUID alunoId = contratos.findAlunoId(id).orElseThrow(() -> erro(HttpStatus.NOT_FOUND, "Contrato não encontrado."));
        var aluno = bloquearAluno(alunoId);
        var contrato = contratoDoProfessor(professor, id);
        if (contrato.getDataInicio() != null) throw erro(HttpStatus.CONFLICT, "Primeiro pagamento já registrado.");
        validarDataPagamento(dto.dataPagamento());
        if (contrato.getDuracaoMeses() == null || contrato.getAulasContratadas() == null)
            throw erro(HttpStatus.CONFLICT, "Contrato antigo precisa ser migrado.");
        if (checkins.existsByAlunoAndContratoIsNullAndStatusInAndAulaDataHoraAfter(
                aluno, CONSOMEM_CREDITO, LocalDateTime.now(clock)))
            throw erro(HttpStatus.CONFLICT, "Existem reservas futuras antigas sem contrato. Regularize-as antes de ativar o pacote.");
        contrato.setDataInicio(dto.dataPagamento());
        contrato.setDataVencimento(dto.dataPagamento().plusMonths(contrato.getDuracaoMeses()));
        contrato.setAtivo(true);
        contratos.saveAndFlush(contrato);
        for (int i = 0; i < contrato.getDuracaoMeses(); i++) {
            var parcela = new Pagamento();
            parcela.setContrato(contrato); parcela.setNumeroParcela(i + 1);
            parcela.setValorCobrado(contrato.getValorAcordado());
            // Cada parcela parte da data original, evitando deriva em meses curtos.
            parcela.setDataVencimento(dto.dataPagamento().plusMonths(i));
            parcela.setStatus(i == 0 ? "PAGO" : "PENDENTE");
            if (i == 0) { parcela.setDataPagamento(dto.dataPagamento()); parcela.setValorPago(parcela.getValorCobrado()); }
            pagamentos.save(parcela);
        }
        pagamentos.flush();
        return resposta(contrato);
    }
    @Transactional
    public PagamentoResponseDTO confirmarPagamento(Usuario professor, UUID id, PagamentoRequestDTO dto) {
        UUID alunoId = pagamentos.findAlunoId(id).orElseThrow(() -> erro(HttpStatus.NOT_FOUND, "Parcela não encontrada."));
        bloquearAluno(alunoId);
        var parcela = pagamentos.findById(id).orElseThrow(() -> erro(HttpStatus.NOT_FOUND, "Parcela não encontrada."));
        contratoDoProfessor(professor, parcela.getContrato().getId());
        validarDataPagamento(dto.dataPagamento());
        if (dto.dataPagamento().isBefore(parcela.getContrato().getDataInicio()))
            throw erro(HttpStatus.BAD_REQUEST, "Pagamento não pode anteceder o início do contrato.");
        if (!"PAGO".equals(parcela.getStatus())) {
            parcela.setStatus("PAGO"); parcela.setValorPago(parcela.getValorCobrado());
            parcela.setDataPagamento(dto.dataPagamento()); pagamentos.saveAndFlush(parcela);
        }
        return parcelaDTO(parcela);
    }
    @Transactional(readOnly = true)
    public List<ContratoResponseDTO> doAluno(Usuario aluno) {
        return contratos.findByAlunoIdOrderByDataInicioDesc(aluno.getId()).stream().map(this::resposta).toList();
    }
    @Transactional(readOnly = true)
    public List<ContratoResponseDTO> doProfessor(Usuario professor) {
        return contratos.findByProfessorId(professor.getId()).stream().map(this::resposta).toList();
    }
    public Contrato validarReserva(Usuario aluno, LocalDateTime horario) {
        var vigentes = contratos.findByAlunoIdOrderByDataInicioDesc(aluno.getId()).stream().filter(this::vigente).toList();
        if (vigentes.size() != 1) throw erro(HttpStatus.CONFLICT, "É necessário ter um único plano ativo e pago para reservar.");
        var contrato = vigentes.get(0);
        if (contrato.getAulasContratadas() == null) throw erro(HttpStatus.CONFLICT, "Contrato antigo precisa ser migrado.");
        if (inadimplente(contrato)) throw erro(HttpStatus.CONFLICT, "Agendamento bloqueado: há parcela vencida sem pagamento.");
        LocalDate dia = horario.toLocalDate();
        if (dia.isBefore(contrato.getDataInicio()) || !dia.isBefore(contrato.getDataVencimento()))
            throw erro(HttpStatus.CONFLICT, "A aula precisa ocorrer dentro da validade do plano.");
        if (consumidos(contrato) >= contrato.getAulasContratadas())
            throw erro(HttpStatus.CONFLICT, "Saldo de aulas esgotado.");
        return contrato;
    }
    private Contrato contratoDoProfessor(Usuario professor, UUID id) {
        var contrato = contratos.findById(id).orElseThrow(() -> erro(HttpStatus.NOT_FOUND, "Contrato não encontrado."));
        if (contrato.getProfessor() == null || !contrato.getProfessor().getId().equals(professor.getId()))
            throw erro(HttpStatus.NOT_FOUND, "Contrato não encontrado.");
        return contrato;
    }
    private boolean vigente(Contrato c) {
        LocalDate hoje = LocalDate.now(clock);
        return Boolean.TRUE.equals(c.getAtivo()) && c.getDataInicio() != null && c.getDataVencimento() != null
            && !hoje.isBefore(c.getDataInicio()) && hoje.isBefore(c.getDataVencimento());
    }
    private boolean inadimplente(Contrato c) {
        return pagamentos.existsByContratoAlunoIdAndStatusNotAndDataVencimentoLessThanEqual(
            c.getAluno().getId(), "PAGO", LocalDate.now(clock));
    }
    private long consumidos(Contrato c) { return checkins.countByContratoAndStatusIn(c, CONSOMEM_CREDITO); }
    private void validarDataPagamento(LocalDate data) {
        if (data == null || data.isAfter(LocalDate.now(clock)))
            throw erro(HttpStatus.BAD_REQUEST, "Data de pagamento deve ser hoje ou uma data passada.");
    }
    private ContratoResponseDTO resposta(Contrato c) {
        String situacao = c.getDataInicio() == null ? "AGUARDANDO_PAGAMENTO"
            : !Boolean.TRUE.equals(c.getAtivo()) ? "INATIVO" : !vigente(c) ? "VENCIDO"
            : inadimplente(c) ? "INADIMPLENTE" : "ATIVO";
        long usados = consumidos(c);
        long saldo = "ATIVO".equals(situacao) && c.getAulasContratadas() != null
            ? Math.max(0, c.getAulasContratadas() - usados) : 0;
        return new ContratoResponseDTO(c.getId(), c.getAluno().getId(), c.getPlano().getNome(),
            c.getAulasContratadas(), usados, saldo, c.getDataInicio(), c.getDataVencimento(),
            c.getValorAcordado(), situacao,
            pagamentos.findByContratoIdOrderByNumeroParcelaAsc(c.getId()).stream().map(this::parcelaDTO).toList());
    }
    private PagamentoResponseDTO parcelaDTO(Pagamento p) {
        String status = !"PAGO".equals(p.getStatus()) && !p.getDataVencimento().isAfter(LocalDate.now(clock))
            ? "ATRASADO" : p.getStatus();
        return new PagamentoResponseDTO(p.getId(), p.getNumeroParcela(), p.getValorCobrado(), p.getValorPago(),
            p.getDataVencimento(), p.getDataPagamento(), status);
    }
    private ResponseStatusException erro(HttpStatus status, String mensagem) {
        return new ResponseStatusException(status, mensagem);
    }
}
