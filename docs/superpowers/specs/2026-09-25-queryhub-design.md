# QueryHub — análise técnica e proposta de MVP

Status: especificação aprovada pelo usuário em 2026-09-25; implementação ainda não iniciada. Plano associado: `../plans/2026-09-25-queryhub-mvp.md`.

## Objetivo e contexto

O diretório `/opt/Projetos/query-flow` está vazio. Os dois anexos fornecidos são a especificação de produto. O sucesso consiste em cadastrar uma conexão PostgreSQL, testá-la, descobrir e selecionar databases, executar SELECT/WITH de leitura e visualizar resultados dinâmicos consolidados e individuais. Falhas devem permanecer isoladas por database. Exportação e auditoria vêm depois desse fluxo funcionar; funcionalidades futuras ficam no roadmap.

Premissas: interface em português, instalação local/rede confiável e credenciais externas de um usuário PostgreSQL com privilégios mínimos. Autenticação e RBAC não fazem parte deste MVP; por isso a configuração Docker padrão ficará vinculada a localhost. Usar a pasta atual como raiz evita criar uma pasta queryhub redundante.

## 1. Arquitetura e alternativas

Recomendação: monólito modular Spring Boot 3.x/Java 21, SPA React e PostgreSQL interno. Os módulos separam conexões, execução, validação, criptografia, exportação e auditoria. JPA/HikariCP acessam exclusivamente o banco interno; JDBC acessa os databases externos.

Alternativas: um serviço separado de execução facilitaria escalar workers, mas adicionaria comunicação e implantação distribuídas. Jobs assíncronos com fila e armazenamento de resultados facilitariam retomada e progresso, mas exigiriam expiração e mais estados. O MVP usará uma requisição síncrona com resposta agregada e execução limitada; um identificador de execução e resultados individuais permitem uma evolução posterior para jobs/SSE.

## 2. Estrutura

```text
backend/
  pom.xml
  Dockerfile
  src/main/java/br/com/queryhub/
    QueryHubApplication.java
    config/ controller/ dto/ exception/
    domain/connection/ domain/audit/ repository/
    service/ util/
  src/main/resources/application.yml
  src/main/resources/db/migration/
  src/test/java/br/com/queryhub/
frontend/
  Dockerfile
  src/app/ src/features/connections/ src/features/query/
  src/components/ src/lib/ src/types/
docker-compose.yml
.env.example
.gitignore
README.md
```

Frontend: React, TypeScript, Vite, Monaco, componentes Radix UI e TanStack Table. Tela de conexões com CRUD/teste; workspace com seletor de conexão, lista pesquisável de databases, editor e resultados. Tabela com paginação local, ordenação, busca, filtro por coluna, scroll, cabeçalho fixo, NULL distinto e cópia de célula/linha. Redimensionamento será incluído se não prejudicar o fluxo principal. Não exibir módulos futuros como funcionalidades disponíveis.

## 3. Modelo de dados

`database_connection`: id bigint, name, description, host, port, initial_database, username, encrypted_password, ssl_enabled, active, created_at e updated_at. Restrições para campos obrigatórios e porta válida. Timestamps com timezone; migrations Flyway e Hibernate em validate.

`query_audit`: id, execution_id UUID, connection_id anulável, connection_name como snapshot, database, executed_at, status, execution_time_ms, row_count, query_fingerprint e error_message sanitizado. Exclusão de conexão preserva auditoria por FK com ON DELETE SET NULL. SQL completo e resultados não serão persistidos automaticamente.

Queries salvas, favoritos, histórico completo e agendamentos não terão tabelas nesta etapa.

## 4. AES-256-GCM

EncryptionService carrega exclusivamente APP_ENCRYPTION_KEY, em Base64, decodificando exatamente 32 bytes. Configuração inválida impede a inicialização. Cada criptografia usa nonce aleatório de 12 bytes e tag de autenticação de 128 bits. O envelope persistido terá versão, nonce e ciphertext/tag em Base64. Usar contexto estável como AAD e testes de adulteração e chave incorreta.

A chave não será gerada silenciosamente no startup. README explicará geração, backup separado e consequência de perda. Rotação exigirá descriptografar com a chave anterior e recriptografar; não será uma função pública do MVP.

## 5. Armazenamento e transporte das credenciais

DTOs de saída nunca incluem senha ou ciphertext. POST exige senha; PUT com senha ausente/vazia preserva o valor atual. Senhas existem no estado do formulário apenas durante a edição, sem localStorage. Não registrar corpos de requisições, entidades ou exceptions JDBC integrais. Credenciais seguem em propriedades JDBC, nunca na URL. Configuração SSL habilitada utilizará verificação de certificado/hostname e documentação de CA confiável.

## 6. JDBC dinâmico

Uma fábrica usa PGSimpleDataSource com host, porta e database em propriedades próprias, evitando concatenação de parâmetros fornecidos pelo usuário em URLs. Cada operação abre e fecha sua conexão; não manter um pool por database. Carregar um snapshot da configuração antes de executar, rejeitar conexões inativas e tratar remoção durante execução de forma previsível.

Toda consulta externa usa transação explicitamente read-only, autocommit desabilitado, cursor/fetch size e rollback ao final. Usar try-with-resources para Connection, Statement e ResultSet. Não compartilhar Connection entre threads.

## 7. Descoberta

Consultar pg_database no database inicial, filtrando datistemplate=false e datallowconn=true e verificando has_database_privilege(..., 'CONNECT'). Retornar nomes reais ordenados. Antes de executar, redescobrir e validar todos os nomes solicitados, deduplicados, contra a lista. Não aceitar nome arbitrário. Database removido ou permissão revogada depois da validação gera erro individual.

## 8. Validação SQL

Usar parser SQL baseado em AST, inicialmente JSqlParser, com versão fixada e testes para a sintaxe PostgreSQL suportada. Aceitar exatamente uma instrução SELECT, inclusive WITH de leitura. Inspecionar recursivamente CTEs, subconsultas e operações de conjuntos; rejeitar nós desconhecidos, CTEs modificadoras, SELECT INTO, cláusulas de bloqueio e todos os comandos de alteração dos anexos. Falha de parsing bloqueia execução; nunca recorrer a regex como fallback permissivo.

Validação sintática não prova existência de tabelas nem ausência de efeitos de funções. Combinar AST, transação read-only e permissões PostgreSQL. Funções administrativas, de acesso externo e funções de usuário exigem política restrita; o MVP aceitará uma lista documentada de funções usuais de leitura e rejeitará funções não aprovadas. Não usar usuário superuser como credencial externa. Mesmo read-only não é uma sandbox completa para funções com efeitos externos.

## 9. ResultSet dinâmico

Cada coluna terá chave estável, label, tipo JDBC e nome do tipo PostgreSQL. Preservar labels duplicados usando chaves únicas, sem sobrescrever valores. `rows` será uma lista de mapas indexada por essas chaves. Valores temporais, UUID, JSON, arrays e binários terão conversão explícita; bigint/numeric serão strings quando necessário para evitar perda de precisão JavaScript. SQL NULL permanece null.

A consolidação unifica colunas por label e ocorrência, respeitando a ordem da primeira aparição. Ausência de uma coluna em um database vira null; metadados indicam tipos divergentes. A primeira coluna terá label DATABASE e chave interna reservada; uma coluna da query chamada DATABASE continua preservada com chave diferente. Abas individuais mantêm metadados originais.

## 10. Concorrência Java 21

Executor de virtual threads com admissão limitada e semáforo global por instância para conexões externas, padrão MAX_CONCURRENT_DATABASES=5. O limite vale entre requisições, incluindo descoberta e teste, não apenas dentro de uma execução. Não manter uma permissão de descoberta enquanto aguarda tarefas de execução. Limitar também execuções admitidas e databases por request; saturação retorna 429, sem fila ilimitada. Cada tarefa produz SUCCESS ou ERROR sem cancelar as demais. A resposta respeita a ordem solicitada.

## 11. Timeout e cancelamento

QUERY_TIMEOUT_SECONDS=60 para cada consulta, com Statement.setQueryTimeout e statement_timeout transacional no servidor. Configurar connect timeout, socket timeout e lock timeout. Tempo aguardando admissão será limitado separadamente e não deve ser confundido com tempo de consulta. Um cancelamento apenas de Future não basta para interromper JDBC: o desenho mantém um contexto de execução capaz de cancelar statements futuramente. Cancelamento público e SSE ficam no roadmap do MVP inicial.

## 12. Limites

MAX_RESULT_ROWS=10000 por database. Ler no máximo limite+1 para detectar truncamento corretamente e retornar somente o limite, com truncated=true. Também aplicar limites documentados de databases, SQL, bytes por célula e orçamento agregado por execução, pois limite de linhas sozinho não controla memória. Exceder um orçamento retorna estado e motivo explícitos, nunca corte silencioso. Exportações respeitam os mesmos limites.

## 13. Contratos REST

| Método e rota | Entrada | Saída |
|---|---|---|
| POST /api/connections | Campos de cadastro com password | 201, ConnectionResponse sem senha |
| GET /api/connections | — | Lista de ConnectionResponse |
| GET /api/connections/{id} | — | ConnectionResponse |
| PUT /api/connections/{id} | Cadastro com password opcional | ConnectionResponse |
| DELETE /api/connections/{id} | — | 204 |
| POST /api/connections/{id}/test | — | success, message, responseTimeMs |
| GET /api/connections/{id}/databases | — | databases: string[] |
| POST /api/query/validate | sql | valid, message, code |
| POST /api/query/execute | connectionId, databases, sql | ExecutionResponse |
| POST /api/query/export/csv | Resultado limitado já recebido | Arquivo CSV |
| POST /api/query/export/xlsx | Resultado limitado já recebido | Arquivo XLSX |

ExecutionResponse contém executionId, connectionId, connectionName, executedAt, executionTimeMs, summary, results e consolidated. Resultado individual: database, status, columns, rows, rowCount, executionTimeMs, truncated e error. Summary: selectedDatabases, successCount, errorCount e rowCount. Error contém code e message sanitizados.

Exports transformam o snapshot exibido sem executar novamente o SQL e sem persistir resultados. O payload é não confiável: validar estrutura, tamanhos e limites; o arquivo não constitui prova de auditoria. CSV inclui DATABASE e neutraliza fórmulas de planilha. XLSX via Apache POI contém Resumo, Consolidado e abas individuais com nomes sanitizados, truncados e únicos; escrever conteúdo como valores, nunca fórmulas provenientes dos resultados.

Erros globais usam ProblemDetail com código estável e mensagem controlada. 400 para entrada/SQL inválidos, 404 para conexão ausente, 429 para saturação e 503 para indisponibilidade global. Erros por database aparecem dentro de uma resposta de execução 200. SQLState permite mapear autenticação, timeout, tabela/coluna ausente e conectividade sem expor mensagens JDBC integrais.

## 14. Docker e operação

Três serviços: queryhub-frontend (Nginx), queryhub-backend e queryhub-postgres. Builds multi-stage Maven/Java 21 e Node/Vite. Frontend em localhost:3000, backend em localhost:8001 e PostgreSQL interno em localhost:5433. Nginx encaminha /api para backend com timeout coerente. Nenhuma porta 8080 no host.

PostgreSQL com volume persistente e healthcheck; backend depende de service_healthy antes do Flyway. Actuator expõe apenas health necessário. .env.example sem segredo válido; .env ignorado. README explicará gerar APP_ENCRYPTION_KEY, configurar DB_PASSWORD e executar docker compose up --build. Não incluir bases externas reais nem credenciais de exemplo funcionais.

## 15. Riscos e validação

- Parser pode rejeitar sintaxe PostgreSQL legítima: documentar subconjunto e falhar de forma fechada.
- SELECT pode chamar funções com efeitos externos: privilégios mínimos e política de funções são necessários além de read-only.
- Sem autenticação, quem acessa o app controla as conexões cadastradas: exposição padrão apenas local; implantação compartilhada depende de controle de acesso externo ou futura autenticação.
- Host configurável permite acesso a redes alcançáveis pelo backend: administrar acesso à aplicação e restringir rede de saída no ambiente de implantação.
- Resultados volumosos ou campos enormes: limites de linhas, bytes e admissões, testados em conjunto.
- Falhas de rede/cancelamento: timeout de conexão, servidor e socket, fechamento de recursos e testes reais.
- Nenhum snapshot transacional único entre databases: consolidação não promete consistência global.
- Auditoria indisponível: registrar falha controlada e devolver aviso explícito; não alegar que execução foi auditada quando persistência falhar.

Testes unitários: criptografia, adulteração, AST e bypasses, labels duplicados, DATABASE colidente, NULL, serialização, CSV e XLSX. Integração com Testcontainers: CRUD sem retorno de senha, edição preservando senha, discovery, credenciais inválidas, timeout, limite exato e excedido, schemas distintos, consultas vazias, falhas parciais e concorrência global entre requests. Testar no navegador o fluxo principal e exports, além de build/typecheck do frontend e Maven verify.

## Sequência de entrega

1. Estrutura, Compose, PostgreSQL interno e Flyway.
2. CRUD criptografado, teste de conexão e descoberta.
3. Validação SQL, execução JDBC limitada e resultados dinâmicos.
4. Interface completa do fluxo principal e testes ponta a ponta.
5. CSV/XLSX, auditoria, revisão de segurança e README.

Critério de aceite: executar a mesma consulta em três databases de teste, consolidar e abrir abas individuais, demonstrar falha isolada, impedir escrita, preservar credenciais e exportar exatamente os dados exibidos.

## Referências técnicas

- PostgreSQL SET TRANSACTION: https://www.postgresql.org/docs/15/sql-set-transaction.html
- JSqlParser, exemplos oficiais de parsing: https://github.com/JSQLParser/JSqlParser/wiki/Examples-of-SQL-parsing
- JSqlParser, validação: https://github.com/JSQLParser/JSqlParser/wiki/Examples-of-SQL-Validation
