# Aquarius Circus — coleção Postman e PostgreSQL

Este pacote testa a API real contra um banco PostgreSQL exclusivo, usando dados fictícios. O executor cria um banco com prefixo `aquarius_postman_test_`, inicia uma cópia do back-end na porta 8081, prepara um professor fictício, executa a coleção e encerra apenas essa cópia da API. Não envia as requisições para a aplicação da porta 8080 e não usa as tabelas do banco `gestao_alunos`.

O banco de teste é mantido para inspeção. Cada execução cria outro banco de teste. O pacote não contém comandos de exclusão de banco.

## Arquivos

- `Aquarius.postman_collection.json`: coleção importável, com 171 requisições principais e cenários concorrentes/SMTP com requisições adicionais.
- `Aquarius-Teste.postman_environment.json`: ambiente com URL de teste e credenciais exclusivamente fictícias.
- `Executar-Testes.ps1`: prepara e executa o processo completo.
- `RunPostman.java`: inicia a API isolada, prepara o professor e verifica as invariantes diretamente no PostgreSQL.
- `executar-newman.cjs`: executa a mesma coleção pelo Newman e gera relatórios sem tokens e sem senha do PostgreSQL.
- `caixa-email-teste.cjs`: caixa SMTP temporária em loopback (porta 2526) e consulta autenticada das mensagens fictícias (porta 8091). Só aceita destinatários `aquarius.test`; não transmite e-mail pela internet.
- `Resultado-Postman.html`: relatório visual da execução mais recente.
- `Resultado-Postman.json`: resultados das requisições e verificações, sem cabeçalhos de autenticação.
- `Verificacao-PostgreSQL.json`: conferência direta das restrições e dos saldos no banco de teste.

## Executar automaticamente

Pré-requisitos: projeto atualizado do Aquarius Circus, Java 17 ou superior, Node.js com npm e PostgreSQL local. O usuário de banco configurado precisa poder criar um banco de testes.

O executor lê `DB_URL`, `DB_USERNAME` e `DB_PASSWORD` do `.env.properties` local ou das variáveis de ambiente. Ele usa apenas host e porta da configuração para criar um banco separado. A senha do PostgreSQL não entra na coleção, no ambiente exportado ou nos relatórios. Não compartilhe seu `.env.properties`.

No PowerShell 7, dentro desta pasta, execute:

```powershell
pwsh -NoProfile -File .\Executar-Testes.ps1 -Projeto "C:\Users\tibia\IdeaProjects\gestao-alunos"
```

A execução compila o JAR, instala Newman 6.2.1 localmente em `target/postman/tools` se necessário e executa os testes. Nenhuma instalação global do Newman é feita. As portas 2526 e 8091 também precisam estar livres para a caixa SMTP de teste. Os relatórios ficam nesta pasta. O log da cópia da aplicação fica em `target/postman/api-postman.log`. API, SMTP e caixa de teste são encerrados ao final.

Se a porta 8081 estiver ocupada, use `-Porta 8082`. O executor ajusta a URL usada no teste. A porta 8080 é recusada. O processo lê a senha local e injeta a configuração somente na cópia da API; não modifica o arquivo de configuração do projeto.

## Abrir no Postman

1. Use **Import** para importar os dois arquivos `.json` com `postman_collection` e `postman_environment` no nome.
2. Selecione o ambiente **Aquarius — PostgreSQL de testes (porta 8081)**.
3. Confira as requisições e scripts de teste. Para executar pelo **Collection Runner**, é necessário ter uma instância da API apontando para o banco isolado e o professor fictício já preparado; o executor acima faz essa preparação automaticamente para sua própria execução e encerra a API ao terminar.
4. Execute a coleção inteira, em ordem, com uma iteração. O primeiro script gera identificadores e datas para a execução; tokens e IDs são capturados automaticamente.

A coleção contém requisições que alteram prazo, criam pacotes, registram pagamentos e consomem créditos. Use somente em ambiente de teste. A proteção da coleção aceita apenas API local, recusa a porta 8080 e exige `autorizarBancoDeTeste=SIM`. Essa proteção não identifica o banco por trás de uma API configurada manualmente: ao preparar uma instância manual, confirme o banco de destino.

As credenciais `professor.postman@aquarius.test` e `AquariusTeste123!` existem somente como fixture no banco criado pelo executor. Não são contas do banco real da cliente.

## Cenários cobertos

- Cadastro e login, dados inválidos, senha incorreta, e-mail repetido e tentativa de criar professor pelo cadastro público.
- Separação entre aluno e professor; consulta isolada dos contratos e proteção contra cancelamento de reserva alheia.
- Prazo configurável e persistido, capacidade fixa de 8 pessoas e rejeição de configurações inválidas.
- Contrato aguardando pagamento, ativação do mensal e prevenção de ativação duplicada.
- Consumo dos créditos sem limite semanal, saldo esgotado e aula fora da validade do pacote.
- Cancelamento no prazo com devolução; nova reserva sem duplicar registro.
- Desistência fora do prazo registrada como falta, sem devolver o crédito, liberando a vaga.
- Duas requisições simultâneas disputando o último crédito em aulas diferentes.
- Duas pessoas disputando a oitava vaga e rejeição de uma nona reserva.
- Trimestral com 12 créditos e três parcelas; bloqueio por inadimplência e desbloqueio após baixa manual.
- Repetição da baixa sem gerar créditos extras.
- Expiração do pacote e renovação sem carregar créditos antigos.
- Lista de participantes sem senha/e-mail e restrição do registro de presença antes do início.
- Edição da própria aula sem perder reservas; impedimento de alterar aula alheia ou remarcar fora da validade dos planos.
- Cancelamento da aula pelo professor, devolução de crédito, preservação do histórico e impedimento de novas reservas.
- Criação de professor somente por professor autenticado.
- Recuperação de senha com entrega SMTP local real, link de uso único, invalidação da senha/JWT anteriores e login com a nova senha.
- Alteração autenticada de senha e bloqueio de pedidos repetidos de recuperação.

Após a coleção, são executadas consultas somente de leitura no banco de testes para conferir:

1. As restrições `uk_checkin_aluno_aula` e `uk_pagamento_contrato_parcela` existem como restrições únicas.
2. Nenhuma aula ultrapassa 8 reservas que ocupam vaga.
3. Nenhum pacote consome mais créditos do que contratou.
4. Não existem reservas duplicadas para o mesmo aluno/aula.
5. Não existem parcelas duplicadas por contrato/número.
6. Os tokens de recuperação são armazenados como hash hexadecimal SHA-256, sem token em texto puro.

## Limites desta validação

O teste usa um banco novo. Ele valida o comportamento da API no PostgreSQL e os cenários de concorrência descritos, mas não converte nem homologa os registros antigos do banco real. A migração de dados antigos, os fluxos visuais do front-end, o limite exato de horário sob relógio controlado e o registro de presença após o início continuam sendo verificações complementares. Os testes Java existentes cobrem os limites de horário com relógio controlado.

Referências oficiais: [Newman](https://learning.postman.com/docs/collections/using-newman-cli/installing-running-newman) e [Collection Runner](https://learning.postman.com/latest-v-12/docs/tests-and-scripts/running-collections/intro-to-collection-runs).
