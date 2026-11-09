# Aquarius Circus — ponto de parada em 05/11/2026

## Implementado nesta etapa

- Aulas com 8 vagas e créditos sem limite semanal.
- Mensais com 4/8/12 créditos e trimestrais com 12/24/36, liberados no primeiro pagamento.
- Validade iniciada pelo primeiro pagamento, expiração das sobras e parcelas mensais do trimestral.
- Confirmação manual de pagamento, bloqueio de novas reservas por inadimplência e consulta do próprio saldo/contrato.
- Retirada do nome com prazo configurável pela gestão (inicialmente 12h); dentro do prazo devolve crédito, depois registra falta e consome o crédito.
- Edição e cancelamento das próprias aulas futuras pelo professor, preservando histórico e devolvendo créditos ao pacote original quando a escola cancela.
- Cadastro público somente de alunos; qualquer professor autenticado pode criar outro professor, conforme decisão do responsável pelo projeto.
- Recuperação por link de uso único com validade de 15 minutos, hash do token no banco e invalidação dos JWTs antigos após trocar a senha.
- Alteração autenticada de senha com conferência da senha atual.
- README, exemplos de configuração privada e coleção Postman com executor PostgreSQL isolado em `postman/`.

## Validação realizada

- Maven `verify`: 80 testes aprovados, sem falhas.
- Coleção Postman/Newman contra PostgreSQL 18.6 separado: 375 verificações aprovadas. O número de requisições varia levemente por causa da consulta assíncrona da caixa de e-mail de teste.
- SMTP local temporário validou o recebimento e uso do link, sem enviar mensagens reais.
- Consultas diretas no banco verificaram unicidade, lotação, créditos, parcelas e armazenamento do hash dos tokens.
- A aplicação local também foi iniciada pelo responsável pelo projeto e conectou ao PostgreSQL.

## Próximos passos

1. Escolher o endereço e o serviço de envio de e-mail do estúdio e configurar SMTP e `PASSWORD_RESET_URL` privadamente. Nenhuma credencial deve ir para o Git.
2. Integrar o front-end às respostas e rotas documentadas, incluindo tela de recuperação de senha e área de gestão.
3. Auditar e migrar dados antigos em uma cópia do banco, preenchendo vínculos e condições dos contratos. Os testes PostgreSQL usaram bancos novos com dados fictícios.
4. Preparar migrações controladas e implantação com HTTPS, backups e monitoramento antes da produção.
5. Avaliar recorrência, fila de espera, notificações e relatórios conforme a prioridade da cliente.

Pagamento automático via Pix/cartão não foi implementado: a baixa atual é manual pela gestão. A recuperação está implementada, mas a entrega real depende do serviço SMTP e da tela do front-end. A fila de e-mail é local, sem persistência ou reenvio automático após falha de entrega.
