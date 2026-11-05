package com.gestao.service;
import com.gestao.domain.*;
import com.gestao.dto.CadastroDTO;
import com.gestao.dto.ProfessorCadastroDTO;
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
        return criar(data.nome(), data.email(), data.senha(), Role.ALUNO);
    }
    @Transactional
    public Usuario cadastrarProfessor(Usuario responsavel, ProfessorCadastroDTO data) {
        if (responsavel == null || responsavel.getRole() != Role.PROFESSOR)
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Somente professores podem criar professores.");
        return criar(data.nome(), data.email(), data.senha(), Role.PROFESSOR);
    }
    private Usuario criar(String nome, String emailInformado, String senha, Role role) {
        validarSenha(senha);
        String email = normalizarEmail(emailInformado);
        if (usuarios.findByEmailIgnoreCase(email).isPresent())
            throw new ResponseStatusException(HttpStatus.CONFLICT, "E-mail já cadastrado.");
        var usuario = new Usuario();
        usuario.setNome(nome.trim()); usuario.setEmail(email);
        usuario.setSenha(encoder.encode(senha)); usuario.setRole(role);
        return usuarios.saveAndFlush(usuario);
    }
    public static void validarSenha(String senha) {
        if (senha == null || senha.isBlank() || senha.length() < 8 || senha.getBytes(StandardCharsets.UTF_8).length > 72)
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Senha deve ter pelo menos 8 caracteres e no máximo 72 bytes UTF-8.");
    }
}
