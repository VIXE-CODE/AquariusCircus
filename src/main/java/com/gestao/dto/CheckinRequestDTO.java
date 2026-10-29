package com.gestao.dto;
import jakarta.validation.constraints.NotNull;
import java.util.UUID;
public record CheckinRequestDTO(@NotNull UUID aulaId) {}
