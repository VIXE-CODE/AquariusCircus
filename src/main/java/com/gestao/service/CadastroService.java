package com.gestao.service;
import com.gestao.domain.*;
import com.gestao.dto.CadastroDTO;
import com.gestao.repository.UsuarioRepository;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import java.nio.charset.StandardCharsets;
import java.util.Locale;

@Service
public class CadastroService {
    private final UsuarioRepository usuarios;
    private final PasswordEncoder encoder;
    public CadastroService(UsuarioRepository usuarios, PasswordEncoder encoder) {
        this.usuarios=usuarios; this.encoder=encoder;
    }
    public static String normalizarEmail(String email) { return email.trim().toLowerCase(Locale.ROOT); }
    @Transactional
    public Usuario cadastrar(CadastroDTO data) {
        if (data.role() != null && data.role() != Role.ALUNO)
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Cadastro público permite apenas alunos.");
        if (data.senha().getBytes(StandardCharsets.UTF_8).length > 72)
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Senha deve ter no máximo 72 bytes UTF-8.");
        String email = normalizarEmail(data.email());
        if (usuarios.findByEmailIgnoreCase(email).isPresent())
            throw new ResponseStatusException(HttpStatus.CONFLICT, "E-mail já cadastrado.");
        var usuario = new Usuario();
        usuario.setNome(data.nome().trim()); usuario.setEmail(email);
        usuario.setSenha(encoder.encode(data.senha())); usuario.setRole(Role.ALUNO);
        return usuarios.saveAndFlush(usuario);
    }
}
