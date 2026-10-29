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
| `PATCH /api/aluno/checkins/{id}/cancelar` | ALUNO | Cancela a própria reserva futura e devolve a vaga; sem corpo |
| `POST /api/professor/aulas` | PROFESSOR | Cria aula futura; retorna 201 |
| `GET /api/professor/aulas` | PROFESSOR | Aulas do professor autenticado, ordenadas por horário |

Corpo de criação de aula:

```json
{"titulo":"Acrobacia","dataHora":"2026-12-01T19:00:00","limiteAlunos":12}
```

Resposta de aula:

```json
{"id":"<uuid>","titulo":"Acrobacia","dataHora":"2026-12-01T19:00:00","limiteAlunos":12,"vagasDisponiveis":12,"professorId":"<uuid>","professorNome":"Professor exemplo"}
```

Resposta de reserva:

```json
{"id":"<uuid>","aulaId":"<uuid>","titulo":"Acrobacia","dataHora":"2026-12-01T19:00:00","status":"CONFIRMADO","dataHoraAgendamento":"2026-11-30T10:00:00"}
```

Reserva já ativa e aula lotada recebem 409; aula/reserva inexistente recebe 404. Reserva de outro aluno recebe 404 para não revelar informações. O cancelamento repetido de uma reserva futura já cancelada retorna seu estado atual. É possível reservar novamente após cancelar, reaproveitando o mesmo registro.

O cancelamento é permitido até o início da aula. Antecedência mínima, créditos, reposição, limites de plano e bloqueios por inadimplência ainda dependem da política da cliente e não foram implementados. `checkin` aqui significa reserva, não confirmação de presença.

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

Planos, contratos e pagamentos ainda têm apenas entidades/repositórios. Não há cobrança, aceite de contrato ou validação de elegibilidade por plano/financeiro. As regras de segurança dessas futuras rotas não significam que os endpoints já existem.

Também permanecem pendentes: fluxo administrativo de professores, edição/cancelamento de aulas, presenças/faltas, recorrência, paginação, notificações, fila de espera, OpenAPI, migrações e implantação. Esta entrega corrige a base e amplia o fluxo de reservas; ainda não representa equivalência completa ao TecnoFit.
