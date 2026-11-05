-- Somente consultas. Executar ANTES de atualizar o esquema, em uma cópia do banco.
-- Não atribui contratos, não altera saldos e não apaga dados.

-- Aulas antigas acima da capacidade definida pela cliente.
SELECT a.id, a.titulo, a.data_hora, COUNT(c.id) AS reservas
FROM aulas a LEFT JOIN checkins c ON c.aula_id = a.id
    AND c.status IN ('CONFIRMADO', 'PRESENTE')
GROUP BY a.id, a.titulo, a.data_hora
HAVING COUNT(c.id) > 8;

-- Conferir se os pacotes antigos precisam de conversão para 4/8/12 aulas por mês.
SELECT * FROM planos;

-- Conferir validade, aluno e pacote antes de preencher os novos campos de contrato.
SELECT * FROM contratos;

-- Conferir o histórico antes de numerar parcelas e vincular reservas aos contratos.
SELECT * FROM pagamentos;
SELECT aluno_id, aula_id, COUNT(*) AS registros
FROM checkins GROUP BY aluno_id, aula_id HAVING COUNT(*) > 1;
