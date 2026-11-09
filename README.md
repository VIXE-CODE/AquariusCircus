# Aquarius Circus — back-end

API de gestão de aulas em Java 17+, Spring Boot 3.2.4, Spring Security, JPA e PostgreSQL.

## Executar no IntelliJ

1. Recarregue o projeto Maven após alterar `pom.xml`.
2. Configure um Project SDK Java 17 ou superior. Foi verificado com Java 21, compilando para Java 17.
3. Inicie o PostgreSQL e mantenha o banco `gestao_alunos` existente.
4. Na configuração de execução de `GestaoAlunosApplication`, use a pasta do projeto como Working directory.
5. Execute `GestaoAlunosApplication`.

O arquivo local `.env.properties`, na raiz do projeto, é importado automaticamente quando existe. Ele deve ficar fora do Git e fora do JAR. Esta instalação local recebeu esse arquivo com a configuração de banco existente e uma nova chave JWT aleatória. Não envie esse arquivo para outra pessoa.

Para configurar outra instalação, copie `.env.properties.example` para `.env.properties` e preencha os valores. Também é possível usar variáveis de ambiente, que têm precedência sobre o arquivo local:

| Variável | Uso |
|---|---|
| `DB_URL` | Padrão `jdbc:postgresql://localhost:5432/gestao_alunos` |
| `DB_USERNAME` | Padrão `postgres` |
| `DB_PASSWORD` | Obrigatória para o PostgreSQL |
| `JWT_SECRET` | Chave aleatória exclusiva, pelo menos 32 bytes UTF-8; não reutilizar o exemplo/testes |
| `CORS_ALLOWED_ORIGINS` | Origens exatas separadas por vírgula; padrões locais nas portas 5173 e 3000 |
| `APP_TIME_ZONE` | Padrão `America/Sao_Paulo` |
| `DDL_AUTO` | `update` para o desenvolvimento existente; produção precisa de migrações e `validate` |

A chave JWT local foi substituída: faça login novamente. A senha real do PostgreSQL não foi alterada. Se a senha antiga já foi compartilhada ou publicada, o responsável pelo banco deve trocá-la e atualizar a configuração local.

## Autenticação e perfis

O cadastro público cria apenas `ALUNO`. `role` pode ser omitido ou enviado como `ALUNO`; `PROFESSOR` recebe 400. Contas de professor existentes continuam válidas. O provisionamento de novos professores precisa de um fluxo administrativo; ele não foi exposto no cadastro público.

As demais rotas exigem o cabeçalho `Authorization: Bearer <token>`. O token expira em 24 horas. Não há refresh token nem revogação individual implementados.

### Cadastro

`POST /api/auth/cadastrar`

```json
{"nome":"Aluno exemplo","email":"aluno@example.com","senha":"SenhaDeExemplo123"}
```

Resposta 201: `id`, `nome`, `email`, `role`. Nunca retorna senha/hash. E-mail é normalizado para minúsculas. Senhas exigem pelo menos 8 caracteres e no máximo 72 bytes UTF-8.

### Login

`POST /api/auth/login`

```json
{"email":"aluno@example.com","senha":"SenhaDeExemplo123"}
```

Resposta 200:

```json
{
  "token":"<jwt>",
  "tipo":"Bearer",
  "expiraEmSegundos":86400,
  "usuario":{"id":"<uuid>","nome":"Aluno exemplo","email":"aluno@example.com","role":"ALUNO"}
}
```

`GET /api/auth/me` devolve o resumo da própria conta autenticada.

## Agenda e reservas

Datas são horários locais do fuso de negócio, sem sufixo `Z`, no formato `2026-12-01T19:00:00`. O front-end deve respeitar `APP_TIME_ZONE`; não tratar esses valores como UTC.

| Método e rota | Perfil | Comportamento |
|---|---|---|
| `GET /api/aluno/aulas` | ALUNO | Aulas futuras ordenadas por horário, incluindo as lotadas com zero vagas |
| `POST /api/aluno/checkin` | ALUNO | Reserva uma vaga; corpo `{"aulaId":"<uuid>"}`; retorna 201 |
| `GET /api/aluno/checkins` | ALUNO | Histórico das próprias reservas, incluindo canceladas |
| `PATCH /api/aluno/checkins/{id}/cancelar` | ALUNO | Retira o nome; devolução do crédito depende do prazo; sem corpo |
| `POST /api/professor/aulas` | PROFESSOR | Cria aula futura; retorna 201 |
| `GET /api/professor/aulas` | PROFESSOR | Aulas do professor autenticado, ordenadas por horário |

Corpo de criação de aula:

```json
{"titulo":"Acrobacia","dataHora":"2026-12-01T19:00:00","limiteAlunos":8}
```

Resposta de aula:

```json
{"id":"<uuid>","titulo":"Acrobacia","dataHora":"2026-12-01T19:00:00","limiteAlunos":8,"vagasDisponiveis":8,"professorId":"<uuid>","professorNome":"Professor exemplo"}
```

Resposta de reserva:

```json
{"id":"<uuid>","aulaId":"<uuid>","titulo":"Acrobacia","dataHora":"2026-12-01T19:00:00","status":"CONFIRMADO","dataHoraAgendamento":"2026-11-30T10:00:00"}
```

Reserva já ativa e aula lotada recebem 409; aula/reserva inexistente recebe 404. Reserva de outro aluno recebe 404 para não revelar informações. Repetir a retirada do nome retorna o estado atual sem devolver créditos adicionais. É possível reservar novamente quando a reserva está `CANCELADO` e a aula permanece ativa, reaproveitando o mesmo registro. Reservas `FALTA` ou `CANCELADO_AULA` não podem ser reativadas pelo aluno.

O prazo de cancelamento sem perda do crédito é configurável pela gestão, inicialmente 12 horas. No instante exato do limite o crédito é devolvido. Depois desse limite, inclusive após o início, a retirada do nome registra `FALTA`, mantém o crédito consumido e libera a vaga. Repetir a requisição não devolve créditos adicionais. A aula continua existindo. `checkin` aqui significa reserva, não confirmação de presença.

Todas as aulas têm capacidade fixa de 8 alunos. `limiteAlunos` pode ser omitido na criação ou enviado como 8. Qualquer outro valor é rejeitado.

As alterações de reserva usam transação e bloqueio pessimista da aula para serializar a disputa por vagas. Existe também unicidade de aluno/aula no mapeamento do banco. Antes de aplicar o esquema em um banco antigo, verificar se há duplicidades; esta alteração não apaga registros automaticamente.

## Erros e integração

As respostas de sucesso agora são JSON em vez de mensagens/token em texto puro. O front-end deve ler `token` no JSON de login. As aulas não incluem mais a entidade inteira do professor.

Erros tratados:

```json
{"status":400,"mensagem":"Dados inválidos.","campos":{"email":"deve ser um endereço de e-mail bem formado"}}
```

- 400: corpo ou campos inválidos.
- 401: autenticação ausente, credenciais inválidas ou token inválido/expirado.
- 403: perfil sem permissão.
- 404: aula ou reserva não encontrada.
- 409: conflito de cadastro/reserva ou regra de horário.

## Testes

Execute o ciclo Maven `test` no IntelliJ, ou `mvnw.cmd test` com `JAVA_HOME` apontando para um JDK válido.

Os testes usam o perfil `test` com H2 em memória e não usam o PostgreSQL real. Cobrem login/JWT, cadastro, permissões, ausência de hash em respostas, validações, isolamento de reservas, cancelamento, nova reserva e concorrência pela última vaga. Isso não substitui homologação de concorrência no PostgreSQL antes da produção.

## Escopo pendente

Planos, contratos e confirmação manual de pagamentos estão implementados conforme a seção de regras comerciais abaixo. Não há integração com banco, Pix, cartão ou gateway, emissão automática de cobrança, estorno ou aceite eletrônico de contrato.

Também permanecem pendentes: front-end administrativo, recorrência, paginação, notificações de alterações nas aulas, fila de espera, OpenAPI, migrações automáticas e implantação. Criação restrita de professores, edição/cancelamento das próprias aulas e recuperação de senha estão descritas abaixo. Presença/falta pode ser registrada manualmente pelo professor da aula após o início. Esta entrega ainda não representa equivalência completa ao TecnoFit.

## Edição e cancelamento de aulas

`PATCH /api/professor/aulas/{id}` exige um professor autenticado e dono da aula. O corpo contém título e horário completos:

```json
{"titulo":"Acrobacia — novo horário","dataHora":"2026-12-02T19:00:00"}
```

Somente aulas futuras e não canceladas podem ser editadas. A capacidade continua sendo 8. As reservas e seus IDs são preservados; a consulta do aluno mostra o novo título/horário. Se houver reservas confirmadas, a nova data deve caber na validade de todos os pacotes envolvidos. Caso contrário, a API retorna 409 sem alterar a aula. Aulas com reservas antigas sem contrato não podem ser remarcadas até a regularização. O prazo para retirada do nome acompanha o novo horário e a configuração atual da gestão.

`PATCH /api/professor/aulas/{id}/cancelar` cancela somente a própria aula futura. O corpo é opcional:

```json
{"motivo":"Manutenção do espaço"}
```

O cancelamento é lógico: não exclui a aula nem as reservas. A aula deixa a agenda disponível do aluno, permanece no histórico do professor e não pode receber novas reservas ou presença. Reservas `CONFIRMADO`, `PRESENTE` ou `FALTA` passam para `CANCELADO_AULA` e deixam de consumir crédito, inclusive se o aluno já tinha desistido fora do prazo. Créditos permanecem no pacote original, com a mesma validade; não são prorrogados ou transferidos. Repetir o cancelamento não duplica créditos. Tentar editar/cancelar aula de outro professor retorna 404; aluno recebe 403 nas rotas de professor.

As respostas de aula ganharam `cancelada`, `dataHoraCancelamento` e `motivoCancelamento`; as de reserva ganharam `aulaCancelada`. Não há restauração de aula cancelada nem notificação automática de remarcação nesta versão. O front-end deve mostrar o estado e o novo horário para os alunos.

## Criação restrita de professores

Por decisão do responsável pelo projeto, **qualquer professor autenticado pode criar outro professor**. O cadastro público continua criando somente alunos.

`POST /api/professor/professores`:

```json
{"nome":"Professor exemplo","email":"professor@example.com","senha":"SenhaInicialExemplo123"}
```

A API define o perfil `PROFESSOR`, normaliza o e-mail, aplica a mesma política de senha e salva o hash BCrypt. Retorna 201 com resumo da conta, sem senha ou hash. E-mail repetido retorna 409. Sem login retorna 401; aluno retorna 403. O primeiro professor continua sendo uma conta previamente provisionada; não há senha mestra, cadastro público privilegiado ou promoção automática de alunos. O novo professor pode alterar a senha inicial pela rota autenticada abaixo.

## Recuperação e alteração de senha

### Solicitar recuperação

`POST /api/auth/recuperar-senha`, sem Authorization:

```json
{"email":"aluno@example.com"}
```

Quando o SMTP está configurado, retorna sempre 202 com a mesma mensagem para e-mail existente, inexistente ou pedido repetido no intervalo de 60 segundos. O token não sai na resposta da API. O envio é assíncrono e só ocorre depois do commit. O link tem token aleatório de 256 bits, expira em 15 minutos e é de uso único. Somente o hash SHA-256 fica no banco. Novo pedido após o intervalo substitui o link anterior. Tokens e links nunca são gravados nos logs.

### Definir nova senha

O front-end lê `token` do link e envia `POST /api/auth/redefinir-senha`, sem Authorization:

```json
{"token":"<token de 43 caracteres recebido por e-mail>","novaSenha":"NovaSenhaExemplo123"}
```

Retorna 200 e orienta novo login. Link inválido, expirado ou reutilizado retorna 400. A senha é atualizada com BCrypt e todos os JWTs anteriores da conta ficam inválidos por meio de uma versão de sessão mantida no banco. Não há login automático depois da recuperação. Requisições simultâneas não podem usar o mesmo link duas vezes.

### Alterar senha estando autenticado

`PATCH /api/auth/senha` exige Bearer Token e a senha atual:

```json
{"senhaAtual":"SenhaAtualExemplo123","novaSenha":"NovaSenhaExemplo123"}
```

Senha atual incorreta retorna 400. Após sucesso, os JWTs anteriores e qualquer link de recuperação pendente são invalidados. Faça login novamente para obter um token válido.

### Configurar o envio real

Um serviço de e-mail é o servidor que entrega as mensagens na caixa de entrada do aluno. A implementação usa SMTP para permitir a escolha do provedor posteriormente. Preencha **somente no `.env.properties` local ou no ambiente de implantação**:

| Variável | Uso |
|---|---|
| `SMTP_HOST` | Servidor de envio fornecido pelo provedor |
| `SMTP_PORT` | Porta informada pelo provedor; padrão 587 |
| `SMTP_USERNAME` / `SMTP_PASSWORD` | Credenciais SMTP do endereço de envio |
| `SMTP_FROM` | Endereço autorizado a enviar pelo provedor |
| `SMTP_AUTH` | Padrão `true`; `false` somente em SMTP local de teste |
| `SMTP_STARTTLS` | Padrão `true`; `false` somente em SMTP local de teste |
| `PASSWORD_RESET_URL` | URL da tela do front-end que recebe o link; padrão local `http://localhost:5173/redefinir-senha` |

Fora de localhost, `PASSWORD_RESET_URL` exige HTTPS e não pode incluir credenciais, query ou fragmento. O link não é construído com o cabeçalho Host da requisição. Sem SMTP e remetente configurados, a aplicação continua iniciando normalmente, mas a rota de recuperação retorna 503 para todos os e-mails, informando a falta de configuração. Falhas de entrega SMTP são registradas genericamente e não alteram a senha. O usuário pode repetir o pedido depois do intervalo. Não há fila durável ou reenvio automático após falha de SMTP nesta versão.

Os testes usam remetente/destinatários fictícios e uma caixa SMTP local, sem envio real. O envio pelo provedor escolhido ainda exige configuração e teste de entrega. A tela de recuperação continua sendo responsabilidade do front-end; ele deve evitar registrar o token e usar `Referrer-Policy: no-referrer` na página do link.

Referências: [OWASP — recuperação de senha](https://cheatsheetseries.owasp.org/cheatsheets/Forgot_Password_Cheat_Sheet.html) e [Spring Boot — envio de e-mail](https://docs.spring.io/spring-boot/3.5/reference/io/email.html).

## Regras comerciais — novembro de 2026

| Pacote | Duração | Créditos liberados no primeiro pagamento |
|---|---|---|
| Mensal 1x | 1 mês | 4 |
| Mensal 2x | 1 mês | 8 |
| Mensal 3x | 1 mês | 12 |
| Trimestral 1x | 3 meses | 12 |
| Trimestral 2x | 3 meses | 24 |
| Trimestral 3x | 3 meses | 36 |

Não há limite semanal ou liberação mensal de créditos. O aluno pode distribuir o pacote como quiser, respeitando saldo, disponibilidade e validade. A reserva consome um crédito imediatamente. Presença, falta e desistência fora do prazo continuam consumindo esse crédito. Só `CANCELADO` devolve o crédito ao mesmo pacote; ele não é transferido para uma renovação.

O contrato começa na data informada na confirmação do primeiro pagamento integral. Mensal: 05/11 a 05/12; trimestral: 05/11 a 05/02. O início é inclusivo e o vencimento exclusivo: no dia 05/12 o mensal anterior já não permite aulas. O dia da aula também precisa estar dentro da validade; não basta reservar antes do vencimento. Sobras expiram e não acumulam na renovação. Não há renovação automática: a gestão cria um novo contrato após o vencimento.

O trimestral gera três parcelas mensais: 05/11, 05/12 e 05/01. Em meses curtos, o vencimento cai no último dia disponível, calculado sempre a partir da data original. Cada parcela é o `valorMensal` acordado. O primeiro pagamento libera todos os créditos do trimestre. Parcelas futuras ainda não vencidas não impedem reservar aulas de meses seguintes.

Uma parcela não paga bloqueia novas reservas a partir do próprio dia do vencimento, sem tolerância. O bloqueio considera dívidas de contratos anteriores também. Confirmar o pagamento integral remove o bloqueio quando não houver outras pendências; não cria novos créditos. O aluno continua podendo entrar na conta, consultar seu contrato e retirar o nome de aulas. Reservas já realizadas não são apagadas automaticamente por inadimplência.

### Novas rotas para o front-end

| Método e rota | Acesso | Função |
|---|---|---|
| `GET /api/planos` | Público | Catálogo de pacotes |
| `POST /api/professor/planos` | PROFESSOR | Criar pacote e definir preço mensal |
| `POST /api/professor/contratos` | PROFESSOR | Vincular pacote a um aluno; fica aguardando pagamento |
| `GET /api/professor/contratos` | PROFESSOR | Consultar contratos criados pelo professor |
| `POST /api/professor/contratos/{id}/primeiro-pagamento` | PROFESSOR responsável | Ativar pacote e registrar primeira parcela |
| `POST /api/professor/pagamentos/{id}/confirmar` | PROFESSOR responsável | Dar baixa integral em uma parcela |
| `GET /api/aluno/contratos` | ALUNO | Seus próprios contratos, saldo, validade, situação e parcelas |
| `GET /api/configuracoes` | Autenticado | Prazo atual e capacidade |
| `PATCH /api/professor/configuracoes` | PROFESSOR | Alterar prazo de cancelamento |
| `GET /api/professor/aulas/{id}/checkins` | Professor da aula | Lista de participantes e identificadores das reservas |
| `PATCH /api/professor/checkins/{id}/presenca` | Professor da aula | Registrar `PRESENTE` ou `FALTA` após o início |

O perfil existente `PROFESSOR` representa a gestão nesta versão. Alunos não podem alterar configurações, planos, contratos ou pagamentos. A área administrativa visual ainda depende do front-end. A configuração fica persistida no banco, sem editar código ou reiniciar a aplicação. Uma alteração do prazo passa a valer para cancelamentos feitos a partir dela, inclusive em reservas já existentes.

Criar pacote:
```json
{"nome":"Trimestral 1x","aulasPorMes":4,"duracaoMeses":3,"valorMensal":150.50}
```
Criar contrato (`valorMensal` opcional substitui o preço padrão somente neste contrato):
```json
{"alunoId":"<uuid>","planoId":"<uuid>","valorMensal":140.00}
```
Confirmar primeiro pagamento ou parcela:
```json
{"dataPagamento":"2026-11-05"}
```
Alterar prazo:
```json
{"antecedenciaCancelamentoHoras":24}
```
Registrar presença/falta:
```json
{"status":"FALTA"}
```

As situações de contrato são `AGUARDANDO_PAGAMENTO`, `ATIVO`, `INADIMPLENTE`, `VENCIDO` e `INATIVO`. `creditosConsumidos` preserva o histórico; `creditosDisponiveis` é zero quando o plano vence ou há bloqueio financeiro. Valores monetários usam `BigDecimal` com duas casas, sem `Double`.

### Transição de dados antigos

Esta entrega não executa a aplicação contra o PostgreSQL real nem converte contratos antigos automaticamente. Os novos campos (duração, créditos contratados, professor responsável e vínculo reserva/contrato) precisam ser preenchidos com dados comerciais reais antes de usar registros antigos. Planos antigos incompletos e reservas futuras sem vínculo são identificados e bloqueados para regularização, em vez de presumir créditos.

Use `docs/verificacao-dados-antigos.sql` como consulta inicial de auditoria antes da atualização. A coluna antiga `limite_aulas_por_semana` pode permanecer fisicamente no banco, mas não impõe limite semanal nesta versão. Defina o pacote correto com a cliente e migre os dados em ambiente de homologação com backup. A restrição de 8 pessoas passa a valer também para aulas antigas; aulas com mais de 8 reservas exigem regularização manual. Não há exclusão automática de reservas existentes.
