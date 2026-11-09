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
public class AgendaService {
    private final AulaRepository aulas;
    private final CheckinRepository checkins;
    private final Clock clock;
    private final PlanoFinanceiroService financeiro;
    private final ConfiguracaoService configuracoes;
    private static final List<String> OCUPAM_VAGA = List.of("CONFIRMADO", "PRESENTE");
    public AgendaService(AulaRepository aulas, CheckinRepository checkins, Clock clock,
                         PlanoFinanceiroService financeiro, ConfiguracaoService configuracoes) {
        this.aulas=aulas; this.checkins=checkins; this.clock=clock;
        this.financeiro=financeiro; this.configuracoes=configuracoes;
    }
    @Transactional(readOnly=true)
    public List<AulaResponseDTO> disponiveis() {
        return aulas.findDisponiveis(LocalDateTime.now(clock)).stream().map(this::aulaDTO).toList();
    }
    @Transactional(readOnly=true)
    public List<AulaResponseDTO> doProfessor(Usuario professor) {
        return aulas.findByProfessorOrderByDataHoraAsc(professor).stream().map(this::aulaDTO).toList();
    }
    @Transactional(readOnly=true)
    public List<ParticipanteResponseDTO> participantes(Usuario professor, UUID aulaId) {
        var aula=aulas.findById(aulaId).orElseThrow(() -> erro(HttpStatus.NOT_FOUND, "Aula não encontrada."));
        if (aula.getProfessor() == null || !aula.getProfessor().getId().equals(professor.getId()))
            throw erro(HttpStatus.NOT_FOUND, "Aula não encontrada.");
        return checkins.findByAulaOrderByDataHoraAgendamentoAsc(aula).stream()
            .map(c -> new ParticipanteResponseDTO(c.getId(), c.getAluno().getId(), c.getAluno().getNome(), c.getStatus()))
            .toList();
    }
    @Transactional
    public AulaResponseDTO criar(Usuario professor, AulaRequestDTO data) {
        exigirAulaFutura(data.dataHora());
        var aula=new Aula(); aula.setTitulo(data.titulo().trim()); aula.setDataHora(data.dataHora());
        if (data.limiteAlunos() != null && data.limiteAlunos() != ConfiguracaoService.CAPACIDADE_AULA)
            throw erro(HttpStatus.BAD_REQUEST, "Toda aula tem capacidade de 8 alunos.");
        aula.setLimiteAlunos(ConfiguracaoService.CAPACIDADE_AULA); aula.setProfessor(professor);
        return aulaDTO(aulas.saveAndFlush(aula));
    }
    @Transactional
    public CheckinResponseDTO reservar(Usuario aluno, UUID aulaId) {
        financeiro.bloquearAluno(aluno.getId());
        // Ordem das travas: aluno e depois aula, em todos os fluxos de reservas.
        var aula=aulas.findComBloqueioById(aulaId).orElseThrow(() -> erro(HttpStatus.NOT_FOUND, "Aula não encontrada."));
        exigirAulaAtiva(aula);
        exigirAulaFutura(aula.getDataHora());
        var reserva=checkins.findByAlunoAndAula(aluno, aula).orElse(null);
        if (reserva != null && !"CANCELADO".equals(reserva.getStatus()))
            throw erro(HttpStatus.CONFLICT, "Você já possui reserva nesta aula.");
        if (checkins.countByAulaAndStatusIn(aula, OCUPAM_VAGA) >= ConfiguracaoService.CAPACIDADE_AULA)
            throw erro(HttpStatus.CONFLICT, "Aula lotada.");
        var contrato=financeiro.validarReserva(aluno, aula.getDataHora());
        if (reserva == null) { reserva=new Checkin(); reserva.setAluno(aluno); reserva.setAula(aula); }
        reserva.setStatus("CONFIRMADO"); reserva.setDataHoraAgendamento(LocalDateTime.now(clock));
        reserva.setContrato(contrato);
        return checkinDTO(checkins.saveAndFlush(reserva));
    }
    @Transactional(readOnly=true)
    public List<CheckinResponseDTO> minhasReservas(Usuario aluno) {
        return checkins.findByAlunoOrderByDataHoraAgendamentoDesc(aluno).stream().map(this::checkinDTO).toList();
    }
    @Transactional
    public CheckinResponseDTO cancelar(Usuario aluno, UUID id) {
        financeiro.bloquearAluno(aluno.getId());
        var aulaId=checkins.findAulaIdDaReserva(id, aluno)
            .orElseThrow(() -> erro(HttpStatus.NOT_FOUND, "Reserva não encontrada."));
        aulas.findComBloqueioById(aulaId)
            .orElseThrow(() -> erro(HttpStatus.NOT_FOUND, "Aula não encontrada."));
        var reserva=checkins.findByIdAndAluno(id, aluno)
            .orElseThrow(() -> erro(HttpStatus.NOT_FOUND, "Reserva não encontrada."));
        if (Boolean.TRUE.equals(reserva.getAula().getCancelada())) return checkinDTO(reserva);
        if ("CANCELADO".equals(reserva.getStatus()) || "FALTA".equals(reserva.getStatus())) return checkinDTO(reserva);
        if (!"CONFIRMADO".equals(reserva.getStatus()))
            throw erro(HttpStatus.CONFLICT, "Esta reserva não pode mais ser cancelada.");
        int horas=configuracoes.consultar().antecedenciaCancelamentoHoras();
        LocalDateTime inicio=reserva.getAula().getDataHora();
        LocalDateTime agora=LocalDateTime.now(clock);
        boolean noPrazo=agora.isBefore(inicio) && !agora.isAfter(inicio.minusHours(horas));
        reserva.setStatus(noPrazo ? "CANCELADO" : "FALTA");
        return checkinDTO(checkins.saveAndFlush(reserva));
    }
    @Transactional
    public CheckinResponseDTO registrarPresenca(Usuario professor, UUID id, PresencaRequestDTO dto) {
        UUID alunoId=checkins.findAlunoId(id).orElseThrow(() -> erro(HttpStatus.NOT_FOUND, "Reserva não encontrada."));
        var aluno=financeiro.bloquearAluno(alunoId);
        var aulaId=checkins.findAulaIdDaReserva(id, aluno).orElseThrow(() -> erro(HttpStatus.NOT_FOUND, "Reserva não encontrada."));
        var aula=aulas.findComBloqueioById(aulaId).orElseThrow(() -> erro(HttpStatus.NOT_FOUND, "Aula não encontrada."));
        exigirAulaAtiva(aula);
        if (!aula.getProfessor().getId().equals(professor.getId())) throw erro(HttpStatus.NOT_FOUND, "Reserva não encontrada.");
        if (aula.getDataHora().isAfter(LocalDateTime.now(clock))) throw erro(HttpStatus.CONFLICT, "A aula ainda não começou.");
        var reserva=checkins.findByIdAndAluno(id, aluno).orElseThrow(() -> erro(HttpStatus.NOT_FOUND, "Reserva não encontrada."));
        if (!"CONFIRMADO".equals(reserva.getStatus()) && !dto.status().equals(reserva.getStatus()))
            throw erro(HttpStatus.CONFLICT, "Não é possível alterar este registro de presença.");
        reserva.setStatus(dto.status());
        return checkinDTO(checkins.saveAndFlush(reserva));
    }
    private void exigirAulaFutura(LocalDateTime horario) {
        if (horario == null || !horario.isAfter(LocalDateTime.now(clock)))
            throw erro(HttpStatus.CONFLICT, "A aula deve estar no futuro.");
    }
    @Transactional
    public AulaResponseDTO editar(Usuario professor, UUID id, AulaEdicaoDTO dto) {
        var aula=aulaDoProfessorComBloqueio(professor, id);
        exigirAulaAtiva(aula);
        exigirAulaFutura(aula.getDataHora());
        exigirAulaFutura(dto.dataHora());
        if (!aula.getDataHora().equals(dto.dataHora())) {
            for (var c:checkins.findByAulaOrderByDataHoraAgendamentoAsc(aula)) {
                if (!"CONFIRMADO".equals(c.getStatus())) continue;
                var contrato=c.getContrato();
                var dia=dto.dataHora().toLocalDate();
                if (contrato == null || contrato.getDataInicio() == null || contrato.getDataVencimento() == null
                        || dia.isBefore(contrato.getDataInicio()) || !dia.isBefore(contrato.getDataVencimento()))
                    throw erro(HttpStatus.CONFLICT, "O novo horário fica fora da validade do plano de um aluno reservado. Cancele a aula ou regularize as reservas.");
            }
        }
        aula.setTitulo(dto.titulo().trim()); aula.setDataHora(dto.dataHora());
        return aulaDTO(aulas.saveAndFlush(aula));
    }
    @Transactional
    public AulaResponseDTO cancelarAula(Usuario professor, UUID id, CancelamentoAulaDTO dto) {
        var aula=aulaDoProfessorComBloqueio(professor, id);
        if (Boolean.TRUE.equals(aula.getCancelada())) return aulaDTO(aula);
        exigirAulaFutura(aula.getDataHora());
        aula.setCancelada(true); aula.setDataHoraCancelamento(LocalDateTime.now(clock));
        aula.setMotivoCancelamento(dto == null || dto.motivo() == null || dto.motivo().isBlank()
            ? "Cancelada pelo professor." : dto.motivo().trim());
        aulas.saveAndFlush(aula);
        // Somente a trava da aula: não adquirir travas de alunos nesta ordem evita deadlock.
        checkins.cancelarPelaEscola(id);
        return aulaDTO(aula);
    }
    private Aula aulaDoProfessorComBloqueio(Usuario professor, UUID id) {
        var aula=aulas.findComBloqueioById(id).orElseThrow(() -> erro(HttpStatus.NOT_FOUND, "Aula não encontrada."));
        if (aula.getProfessor() == null || !aula.getProfessor().getId().equals(professor.getId()))
            throw erro(HttpStatus.NOT_FOUND, "Aula não encontrada.");
        return aula;
    }
    private void exigirAulaAtiva(Aula aula) {
        if (Boolean.TRUE.equals(aula.getCancelada())) throw erro(HttpStatus.CONFLICT, "Esta aula foi cancelada pelo professor.");
    }
    private AulaResponseDTO aulaDTO(Aula aula) {
        var p=aula.getProfessor();
        long vagas=Math.max(0, ConfiguracaoService.CAPACIDADE_AULA-checkins.countByAulaAndStatusIn(aula, OCUPAM_VAGA));
        if (Boolean.TRUE.equals(aula.getCancelada())) vagas=0;
        return new AulaResponseDTO(aula.getId(), aula.getTitulo(), aula.getDataHora(), ConfiguracaoService.CAPACIDADE_AULA, vagas,
            p == null ? null : p.getId(), p == null ? null : p.getNome(), Boolean.TRUE.equals(aula.getCancelada()),
            aula.getDataHoraCancelamento(), aula.getMotivoCancelamento());
    }
    private CheckinResponseDTO checkinDTO(Checkin c) {
        return new CheckinResponseDTO(c.getId(), c.getAula().getId(), c.getAula().getTitulo(),
            c.getAula().getDataHora(), c.getStatus(), c.getDataHoraAgendamento(), Boolean.TRUE.equals(c.getAula().getCancelada()));
    }
    private ResponseStatusException erro(HttpStatus status, String message) {
        return new ResponseStatusException(status, message);
    }
}
