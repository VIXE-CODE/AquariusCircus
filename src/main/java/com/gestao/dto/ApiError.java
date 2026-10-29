package com.gestao.dto;
import java.util.Map;
public record ApiError(int status, String mensagem, Map<String, String> campos) {
    public ApiError(int status, String mensagem) { this(status, mensagem, Map.of()); }
}
