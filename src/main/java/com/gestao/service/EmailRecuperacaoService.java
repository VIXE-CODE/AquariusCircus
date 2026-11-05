package com.gestao.service;
import org.slf4j.*;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.mail.*;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Service;
import org.springframework.transaction.event.*;
import java.net.URI;
import java.util.concurrent.Executor;
import java.util.concurrent.RejectedExecutionException;
@Service
public class EmailRecuperacaoService {
    private static final Logger log=LoggerFactory.getLogger(EmailRecuperacaoService.class);
    private final ObjectProvider<JavaMailSender> sender;
    private final String host,remetente,url;
    private final Executor executor;
    public EmailRecuperacaoService(ObjectProvider<JavaMailSender> sender,
        @Value("${spring.mail.host:}") String host,
        @Value("${app.recuperacao.remetente:}") String remetente,
        @Value("${app.recuperacao.url:http://localhost:5173/redefinir-senha}") String url,
        @Qualifier("emailExecutor") Executor executor) {
        this.sender=sender;this.host=host;this.remetente=remetente;this.url=url;
        this.executor=executor;
        var uri=URI.create(url);
        if (uri.getHost()==null || !java.util.List.of("http","https").contains(uri.getScheme())
                || uri.getRawQuery()!=null || uri.getRawFragment()!=null || uri.getUserInfo()!=null)
            throw new IllegalArgumentException("PASSWORD_RESET_URL deve ser uma URL absoluta sem query, fragmento ou credenciais.");
        if ("http".equals(uri.getScheme()) && !java.util.List.of("localhost","127.0.0.1").contains(uri.getHost()))
            throw new IllegalArgumentException("Use HTTPS para recuperação de senha fora do ambiente local.");
    }
    public boolean configurado() { return !host.isBlank() && !remetente.isBlank() && sender.getIfAvailable()!=null; }
    @TransactionalEventListener(phase=TransactionPhase.AFTER_COMMIT)
    public void enviar(RecuperacaoSolicitada evento) {
        try { executor.execute(() -> entregar(evento)); }
        catch (RejectedExecutionException e) {
            log.warn("Fila de recuperação ocupada. Um pedido de envio não pôde ser processado.");
        }
    }
    private void entregar(RecuperacaoSolicitada evento) {
        try {
            var mensagem=new SimpleMailMessage();
            mensagem.setFrom(remetente);mensagem.setTo(evento.email());
            mensagem.setSubject("Aquarius Circus — recuperação de senha");
            mensagem.setText("Recebemos um pedido para redefinir sua senha. O link é válido por 15 minutos e só pode ser usado uma vez.\n\n"
                +url+"?token="+evento.token()+"\n\nSe você não fez esse pedido, ignore esta mensagem.");
            sender.getObject().send(mensagem);
        } catch (MailException | IllegalStateException e) {
            // Não incluir mensagem, destinatário ou exceção SMTP: podem conter o link secreto.
            log.warn("Não foi possível entregar um e-mail de recuperação. Verifique o serviço SMTP.");
        }
    }
}
