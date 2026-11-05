package com.gestao.dto;
import java.util.UUID;
public record ParticipanteResponseDTO(UUID checkinId, UUID alunoId, String alunoNome, String status) {}
