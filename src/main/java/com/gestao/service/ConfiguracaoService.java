package com.gestao.service;
import com.gestao.domain.ConfiguracaoEstudio;
import com.gestao.dto.*;
import com.gestao.repository.ConfiguracaoEstudioRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
@Service
public class ConfiguracaoService {
    public static final int CAPACIDADE_AULA = 8;
    private final ConfiguracaoEstudioRepository repository;
    public ConfiguracaoService(ConfiguracaoEstudioRepository repository) { this.repository = repository; }
    @Transactional(readOnly = true)
    public ConfiguracaoResponseDTO consultar() {
        int horas = repository.findById(1L).map(ConfiguracaoEstudio::getAntecedenciaCancelamentoHoras).orElse(12);
        return new ConfiguracaoResponseDTO(horas, CAPACIDADE_AULA);
    }
    @Transactional
    public ConfiguracaoResponseDTO atualizar(ConfiguracaoRequestDTO dto) {
        var config = repository.findById(1L).orElseGet(ConfiguracaoEstudio::new);
        config.setAntecedenciaCancelamentoHoras(dto.antecedenciaCancelamentoHoras());
        repository.saveAndFlush(config);
        return consultar();
    }
}
