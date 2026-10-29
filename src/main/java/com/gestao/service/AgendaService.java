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
    public AgendaService(AulaRepository aulas, CheckinRepository checkins, Clock clock) {
        this.aulas=aulas; this.checkins=checkins; this.clock=clock;
    }
    @Transactional(readOnly=true)
    public List<AulaResponseDTO> disponiveis() {
        return aulas.findByDataHoraAfterOrderByDataHoraAsc(LocalDateTime.now(clock)).stream().map(this::aulaDTO).toList();
    }
    @Transactional(readOnly=true)
    public List<AulaResponseDTO> doProfessor(Usuario professor) {
        return aulas.findByProfessorOrderByDataHoraAsc(professor).stream().map(this::aulaDTO).toList();
    }
    @Transactional
    public AulaResponseDTO criar(Usuario professor, AulaRequestDTO data) {
        exigirAulaFutura(data.dataHora());
        var aula=new Aula(); aula.setTitulo(data.titulo().trim()); aula.setDataHora(data.dataHora());
        aula.setLimiteAlunos(data.limiteAlunos()); aula.setProfessor(professor);
        return aulaDTO(aulas.saveAndFlush(aula));
    }
    @Transactional
    public CheckinResponseDTO reservar(Usuario aluno, UUID aulaId) {
        // Todas as alterações de reserva bloqueiam a mesma aula até o commit.
        var aula=aulas.findComBloqueioById(aulaId).orElseThrow(() -> erro(HttpStatus.NOT_FOUND, "Aula não encontrada."));
        exigirAulaFutura(aula.getDataHora());
        var reserva=checkins.findByAlunoAndAula(aluno, aula).orElse(null);
        if (reserva != null && !"CANCELADO".equals(reserva.getStatus()))
            throw erro(HttpStatus.CONFLICT, "Você já possui reserva nesta aula.");
        if (checkins.countByAulaAndStatusNot(aula, "CANCELADO") >= aula.getLimiteAlunos())
            throw erro(HttpStatus.CONFLICT, "Aula lotada.");
        if (reserva == null) { reserva=new Checkin(); reserva.setAluno(aluno); reserva.setAula(aula); }
        reserva.setStatus("CONFIRMADO"); reserva.setDataHoraAgendamento(LocalDateTime.now(clock));
        return checkinDTO(checkins.saveAndFlush(reserva));
    }
    @Transactional(readOnly=true)
    public List<CheckinResponseDTO> minhasReservas(Usuario aluno) {
        return checkins.findByAlunoOrderByDataHoraAgendamentoDesc(aluno).stream().map(this::checkinDTO).toList();
    }
    @Transactional
    public CheckinResponseDTO cancelar(Usuario aluno, UUID id) {
        var aulaId=checkins.findAulaIdDaReserva(id, aluno)
            .orElseThrow(() -> erro(HttpStatus.NOT_FOUND, "Reserva não encontrada."));
        aulas.findComBloqueioById(aulaId)
            .orElseThrow(() -> erro(HttpStatus.NOT_FOUND, "Aula não encontrada."));
        var reserva=checkins.findByIdAndAluno(id, aluno)
            .orElseThrow(() -> erro(HttpStatus.NOT_FOUND, "Reserva não encontrada."));
        exigirAulaFutura(reserva.getAula().getDataHora());
        if ("CANCELADO".equals(reserva.getStatus())) return checkinDTO(reserva);
        if (!"CONFIRMADO".equals(reserva.getStatus()))
            throw erro(HttpStatus.CONFLICT, "Esta reserva não pode mais ser cancelada.");
        reserva.setStatus("CANCELADO");
        return checkinDTO(checkins.saveAndFlush(reserva));
    }
    private void exigirAulaFutura(LocalDateTime horario) {
        if (horario == null || !horario.isAfter(LocalDateTime.now(clock)))
            throw erro(HttpStatus.CONFLICT, "A aula deve estar no futuro.");
    }
    private AulaResponseDTO aulaDTO(Aula aula) {
        var p=aula.getProfessor();
        long vagas=Math.max(0, aula.getLimiteAlunos()-checkins.countByAulaAndStatusNot(aula, "CANCELADO"));
        return new AulaResponseDTO(aula.getId(), aula.getTitulo(), aula.getDataHora(), aula.getLimiteAlunos(), vagas,
            p == null ? null : p.getId(), p == null ? null : p.getNome());
    }
    private CheckinResponseDTO checkinDTO(Checkin c) {
        return new CheckinResponseDTO(c.getId(), c.getAula().getId(), c.getAula().getTitulo(),
            c.getAula().getDataHora(), c.getStatus(), c.getDataHoraAgendamento());
    }
    private ResponseStatusException erro(HttpStatus status, String message) {
        return new ResponseStatusException(status, message);
    }
}
