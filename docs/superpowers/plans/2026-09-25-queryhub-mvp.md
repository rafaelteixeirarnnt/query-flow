# QueryHub MVP Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Entregar cadastro seguro de conexões PostgreSQL, descoberta, consultas de leitura em múltiplos databases, resultados dinâmicos, exports e auditoria.

**Architecture:** Monólito modular Spring Boot com JPA/HikariCP apenas no PostgreSQL interno e JDBC direto para servidores externos. SPA React consome DTOs e apresenta resultados em tabelas dinâmicas. Execução síncrona usa virtual threads com admissão e conexões limitadas globalmente por instância.

**Tech Stack:** Java 21, Spring Boot 3.x, Maven, Flyway, PostgreSQL JDBC, JSqlParser, Apache POI; React, TypeScript, Vite, Radix UI, Monaco, TanStack Table; Docker Compose, JUnit, Testcontainers, Vitest e testes de navegador.

**Spec:** `docs/superpowers/specs/2026-09-25-queryhub-design.md` — aprovada pelo usuário em 2026-09-25.

## Global Constraints

- Java 21 + Spring Boot 3.x; não usar Python.
- APP_ENCRYPTION_KEY em Base64 decodifica exatamente 32 bytes; AES-256-GCM com nonce de 12 bytes e tag de 128 bits.
- QUERY_TIMEOUT_SECONDS=60, MAX_RESULT_ROWS=10000 por database, MAX_CONCURRENT_DATABASES=5 por instância.
- JPA somente no banco interno; consultas externas exclusivamente JDBC e read-only.
- Não persistir SQL completo nem resultados; não retornar/logar credenciais ou ciphertext.
- `.env` ignorado; frontend localhost:3000, backend localhost:8001, PostgreSQL interno localhost:5433.
- Resultados parciais preservados; exports usam o snapshot exibido sem executar a consulta novamente.
- UI em português; nenhuma funcionalidade de roadmap será apresentada como pronta.
- Arquivos Java abaixo são relativos a `backend/src/main/java/br/com/queryhub/`; testes Java a `backend/src/test/java/br/com/queryhub/`.
- Commits por tarefa quando houver repositório Git; hoje a pasta não contém `.git`. Não criar worktree antes de existir repositório.

## Review Focus

1. Colunas com labels repetidos, incluindo DATABASE: nenhum valor deve ser sobrescrito (tarefa 5).
2. Alteração de conexão e troca de seleção durante chamadas frontend: respostas antigas não substituem a seleção atual (tarefas 2 e 7).
3. Duas execuções simultâneas com testes/descobertas: nunca ultrapassar o limite global nem deixar permissões presas (tarefas 3 e 6).
4. Campos grandes, muitos databases e exports manipulados: recusar excesso com erro explícito e memória limitada (tarefas 5, 6 e 8).
5. SQL aparentemente SELECT contendo CTE modificadora, função não aprovada ou locks: rejeitar antes da execução e manter proteção no servidor (tarefa 4).

## Decisões operacionais para execução

Fixar versões exatas compatíveis e lockfile durante a implementação, verificando documentação oficial e disponibilidade no registry. Não usar dependências SNAPSHOT. O Java encontrado no host é 25; validar Java 21 via JDK disponível ou imagem Docker, sem alterar o Java global. Presença do comando Docker não confirma daemon disponível.

Limites adicionais propostos: MAX_DATABASES_PER_QUERY=50; MAX_ACTIVE_EXECUTIONS=2; MAX_SQL_CHARS=100000; MAX_CELL_BYTES=1048576; MAX_EXECUTION_BYTES=33554432; MAX_EXPORT_BODY_BYTES=67108864; ADMISSION_TIMEOUT_SECONDS=5; CONNECT_TIMEOUT_SECONDS=10. Resultado excedendo limite de célula retorna erro individual RESULT_TOO_LARGE. Orçamento agregado pode truncar resultados com motivo explícito. Todas as configurações positivas são validadas na inicialização.

Interfaces são contratos mínimos. DTOs serão Java records em arquivos próprios, salvo enums associados. Testes de integração `*IT` executam no Maven Failsafe; testes unitários `*Test` no Surefire. Falta de Docker deve ser reportada como bloqueio de integração, nunca contabilizada como aprovação.

### Task 1: Aplicação e infraestrutura executáveis

**Files:** criar `backend/pom.xml`, `backend/Dockerfile`, `QueryHubApplication.java`, `config/QueryProperties.java`, `backend/src/main/resources/application.yml`, `frontend/package.json`, `frontend/package-lock.json`, `frontend/tsconfig.json`, `frontend/vite.config.ts`, `frontend/index.html`, `frontend/src/main.tsx`, `frontend/src/app/App.tsx`, `frontend/Dockerfile`, `frontend/nginx.conf`, `docker-compose.yml`, `.env.example`, `.gitignore`, `.dockerignore`, `README.md`.

**Tests:** `ApplicationStartupIT.java`, `config/QueryPropertiesTest.java`.

**Interfaces:** disponibilizar `/actuator/health` e proxy `/api`; `QueryProperties` expõe limites tipados acima. Frontend inicialmente mostra estado de serviço e casca de navegação.

- [ ] Escrever teste que exige contexto iniciado com PostgreSQL Testcontainer, health UP e `QueryProperties.maxConcurrentDatabases()==5`; configuração de concorrência 0 deve falhar.
- [ ] Executar `mvn -f backend/pom.xml test` e registrar falha inicial por aplicação/configuração ausente após criar o descriptor Maven mínimo de testes.
- [ ] Implementar bootstrap, propriedades validadas, imagens Java 21, serviços Compose com healthchecks e portas vinculadas a 127.0.0.1. Backend aguarda PostgreSQL saudável; usar Hibernate validate e Flyway.
- [ ] Executar `mvn -f backend/pom.xml verify`, `npm --prefix frontend ci`, `npm --prefix frontend run build` e `docker compose --env-file .env.example config --quiet`. Esperado: sucesso; Compose não deve precisar de segredos reais para validar estrutura.
- [ ] Registrar instruções iniciais de execução e checkpoint/commit `chore: bootstrap QueryHub services`.

### Task 2: Credenciais criptografadas e CRUD persistente

**Files:** `service/EncryptionService.java`, `service/DatabaseConnectionService.java`, `domain/connection/DatabaseConnection.java`, `repository/DatabaseConnectionRepository.java`, `dto/ConnectionRequest.java`, `dto/ConnectionResponse.java`, `dto/ConnectionSnapshot.java`, `controller/ConnectionController.java`, `exception/ApiExceptionHandler.java`, `exception/ApiException.java`, `backend/src/main/resources/db/migration/V1__database_connection.sql`.

**Tests:** `service/EncryptionServiceTest.java`, `controller/ConnectionControllerIT.java`.

**Interfaces:** `EncryptionService.encrypt(String):String`, `decrypt(String):String`; `DatabaseConnectionService.create(ConnectionRequest):ConnectionResponse`, `update(long,ConnectionRequest):ConnectionResponse`, `list():List<ConnectionResponse>`, `get(long):ConnectionResponse`, `delete(long):void`, `snapshot(long):ConnectionSnapshot`. Snapshot interno inclui ciphertext, nunca senha em claro, e não pode ser serializado por controllers.

- [ ] Escrever testes `roundTripAndUniqueNonce`, `rejectsTamperedCiphertext`, `rejectsWrongKey`, `rejectsInvalidKeyLength`: roundtrip preserva Unicode; duas criptografias diferem; adulteração/chave incorreta lançam exceção controlada.
- [ ] Rodar `mvn -f backend/pom.xml -Dtest=EncryptionServiceTest test`; confirmar falha antes de implementar serviço.
- [ ] Implementar envelope `v1:base64(nonce):base64(ciphertext+tag)` com SecureRandom e AAD `queryhub:connection-password:v1`. Evitar toString de objetos com credenciais.
- [ ] Escrever `ConnectionControllerIT`: POST 201; resposta sem password/encryptedPassword; ciphertext persistido diferente da senha; PUT com senha ausente e vazia preserva ciphertext; troca explícita muda ciphertext; desativação persiste; delete 204/get 404; porta inválida 400 sem ecoar senha. Atualizações simultâneas não sobrescrevem silenciosamente: usar optimistic locking e 409 em conflito.
- [ ] Confirmar testes CRUD falhando; implementar migration, entidade, repository, DTOs e controller com Bean Validation. Password vazio em POST é inválido; não aplicar trim em senhas reais.
- [ ] Rodar `mvn -f backend/pom.xml verify`; esperado: testes verdes com PostgreSQL real, incluindo reinicialização do contexto preservando registros. Checkpoint/commit `feat: persist encrypted PostgreSQL connections`.

### Task 3: Teste de conexão, limite global e descoberta

**Files:** `service/ExternalConnectionFactory.java`, `service/ExternalConnectionLimiter.java`, `service/DatabaseDiscoveryService.java`, `service/ConnectionTestService.java`, `util/SqlErrorMapper.java`, `dto/ConnectionTestResponse.java`, `dto/DatabaseListResponse.java`; ampliar `controller/ConnectionController.java`.

**Tests:** `service/ExternalConnectionLimiterTest.java`, `service/DatabaseDiscoveryIT.java`, `service/ConnectionTestIT.java`.

**Interfaces:** `ExternalConnectionLimiter.acquire():Permit` com Permit AutoCloseable; `ExternalConnectionFactory.open(ConnectionSnapshot,String):Connection`; `DatabaseDiscoveryService.discover(ConnectionSnapshot):List<String>`; `ConnectionTestService.test(long):ConnectionTestResponse`; `SqlErrorMapper.map(SQLException):QueryError` com `dto/QueryError.java` (code,message).

- [ ] Escrever teste com contador atômico e chamadas concorrentes: pico de conexões <=5, timeout aguardando permit, permit liberado após exceção e interrupção preservada. Confirmar falha com `mvn -f backend/pom.xml -Dtest=ExternalConnectionLimiterTest test`.
- [ ] Implementar semáforo único compartilhado por teste, discovery e execução. Factory usa PGSimpleDataSource, propriedades separadas, connect/socket timeouts e sslmode verify-full quando habilitado. Descriptografar apenas ao abrir conexão.
- [ ] Escrever testes reais para credenciais válidas/inválidas, servidor indisponível, database com espaços, nomes não-template ordenados, permissão CONNECT revogada e conexão inativa. Mensagens não podem conter password/URL com credenciais.
- [ ] Confirmar falhas; implementar query pg_database com filtros e has_database_privilege, endpoints e mapeamento de SQLState para mensagens controladas. Testar validação da conexão com timeout e tempo decorrido.
- [ ] Executar `mvn -f backend/pom.xml verify`; esperado: descoberta e teste fecham conexões mesmo em erro. Checkpoint/commit `feat: test connections and discover databases`.

### Task 4: Política SQL verificável no backend

**Files:** `service/QueryValidationService.java`, `service/ReadOnlySqlPolicy.java`, `dto/ValidationRequest.java`, `dto/ValidationResponse.java`, `controller/QueryController.java`.

**Tests:** `service/QueryValidationServiceTest.java`, `service/ReadOnlyTransactionIT.java`.

**Interfaces:** `QueryValidationService.validate(String):ValidationResponse`, `requireValid(String):void`. Validação sem executar SQL; `ValidationResponse(valid,message,code)`.

- [ ] Escrever testes parametrizados: SELECT simples, WITH SELECT, UNION, comentários e string contendo DELETE são válidos; instruções múltiplas, WITH DELETE RETURNING, SELECT INTO, FOR UPDATE/SHARE, DDL/DML/COPY/CALL/DO e nós desconhecidos são inválidos. SQL acima de 100000 caracteres é rejeitado.
- [ ] Escrever testes de função: count/sum/avg/min/max/lower/upper/coalesce/nullif/date_trunc são permitidos; função desconhecida, schema de usuário, dblink, set_config, pg_sleep e pg_advisory_lock são rejeitados. Expressões especiais CASE/CAST têm visitantes explícitos; função permitida não deve resolver para homônimo em schema de usuário.
- [ ] Rodar `mvn -f backend/pom.xml -Dtest=QueryValidationServiceTest test`; confirmar falha. Implementar visitantes AST com política fechada recursiva, parser com limite de tempo e uma instrução. Proteger resolução de funções usando search_path controlado com pg_catalog primeiro e schema padrão documentado; não aceitar referências de função qualificadas arbitrariamente.
- [ ] Implementar `/api/query/validate` com retorno 200 contendo valid=false para SQL rejeitado; execute usa a mesma política e retorna 400. Não alegar validação de existência de tabelas.
- [ ] Testar em PostgreSQL que transação read-only rejeita escrita mesmo se a política AST não for chamada no fixture. Executar `mvn -f backend/pom.xml verify`; esperado: suíte de bypasses verde. Checkpoint/commit `feat: enforce read-only SQL policy`.

### Task 5: Resultado JDBC dinâmico e consolidação

**Files:** `dto/ColumnMetadata.java`, `dto/DatabaseQueryResult.java`, `dto/TabularResult.java`, `dto/ExecutionSummary.java`, `dto/ExecutionResponse.java`, `dto/ExecuteQueryRequest.java`, `util/JdbcValueConverter.java`, `service/ResultSetReader.java`, `service/ResultConsolidator.java`, `service/ExecutionBudget.java`.

**Tests:** `service/ResultSetReaderIT.java`, `service/ResultConsolidatorTest.java`.

**Interfaces:** `ColumnMetadata(key,label,jdbcType,typeName,sourceTypes)`; `DatabaseQueryResult(database,status,columns,rows,rowCount,executionTimeMs,truncated,truncationReason,error)`; `TabularResult(columns,rows)`; `ExecuteQueryRequest(connectionId,databases,sql)`; `ExecutionResponse(executionId,connectionId,connectionName,executedAt,executionTimeMs,summary,results,consolidated,warnings)`; `ResultConsolidator.consolidate(List<DatabaseQueryResult>):TabularResult`; `ResultSetReader.read(ResultSet,ExecutionBudget):ReadResult` definido em `dto/ReadResult.java`.

- [ ] Escrever fixture JDBC com labels `id,id,DATABASE`, null, string vazia, bigint acima de 2^53, numeric, timestamptz, UUID, JSON, array e bytea. Assertar três chaves distintas, null preservado, precisão decimal/bigint preservada como string e binário Base64. Resultado vazio ainda retorna colunas.
- [ ] Escrever testes com 10000 e 10001 linhas: apenas segundo caso truncated=true. Campo >1048576 bytes gera RESULT_TOO_LARGE, sem publicar valor parcial; orçamento agregado de 33554432 bytes não pode ser ultrapassado entre threads.
- [ ] Confirmar falhas com `mvn -f backend/pom.xml verify`; implementar conversões explícitas, limite+1, leitura controlada e orçamento thread-safe. Restringir colunas/estruturas complexas para impedir alocações não limitadas; registrar que o driver pode materializar campos antes da conversão e exigir limites de memória no processo.
- [ ] Escrever consolidator test: primeira coluna é DATABASE com chave `__database`; coluna original DATABASE permanece; união por label+ocorrência; colunas faltantes null; metadados indicam tipos diferentes. Confirmar falha e implementar.
- [ ] Executar `mvn -f backend/pom.xml verify`; esperado: nenhuma sobrescrita e limites corretos. Checkpoint/commit `feat: map and consolidate dynamic query results`.

### Task 6: Execução multi-database com falhas isoladas

**Files:** `service/DynamicQueryService.java`, `service/DatabaseQueryRunner.java`, `service/ExecutionContext.java`, `config/QueryExecutorConfiguration.java`; ampliar `controller/QueryController.java`.

**Tests:** `service/DynamicQueryServiceIT.java`, `service/ExecutionAdmissionTest.java`.

**Interfaces:** `DynamicQueryService.execute(ExecuteQueryRequest):ExecutionResponse`; `DatabaseQueryRunner.run(ConnectionSnapshot,String,String,ExecutionContext):DatabaseQueryResult`; `ExecutionContext` contém UUID, orçamento, registro de statements e método `cancel():void` para evolução futura, sem endpoint público.

- [ ] Escrever teste de três databases: dois retornam dados e um falha por tabela ausente; resposta contém dois SUCCESS, um ERROR e dados consolidados dos dois. Testar coluna ausente, resultado vazio e nomes duplicados no request deduplicados em ordem.
- [ ] Escrever teste `rejectsUndiscoveredDatabaseBeforeExecution`: nome não descoberto retorna 400, nenhum worker iniciado. Conexão inativa falha antes de discovery.
- [ ] Escrever testes de admissão: máximo 2 execuções ativas; terceira 429; >50 databases 400; concorrência externa total <=5 somando dois requests/teste/discovery; release após falhas. Confirmar falhas antes de implementar.
- [ ] Implementar snapshot, validation, admission, discovery/revalidação e fan-out com virtual threads. Discovery libera permit antes de lançar workers. Resultados mantêm ordem de entrada, captura de erro por worker e duração monotônica.
- [ ] Implementar runner com transação read-only, search_path controlado, timeout JDBC/servidor 60s, fetch size, rollback e try-with-resources. Em teste usar timeout 1s e uma view fixture lenta, evitando liberar pg_sleep na política pública. Usar socket timeout maior que statement timeout.
- [ ] Executar `mvn -f backend/pom.xml verify`; assertar consulta lenta cancelada, conexão fechada e consulta seguinte funcionando. Checkpoint/commit `feat: execute bounded multi-database queries`.

### Task 7: Fluxo principal React integrado

**Files:** `frontend/src/lib/api.ts`, `frontend/src/types/api.ts`, `frontend/src/app/App.tsx`, `frontend/src/app/styles.css`, `frontend/src/features/connections/ConnectionsPage.tsx`, `ConnectionDialog.tsx` no mesmo diretório, `frontend/src/features/query/QueryWorkspace.tsx`, `DatabaseSelector.tsx`, `SqlEditor.tsx`, `ExecutionResults.tsx` no mesmo diretório, `frontend/src/components/DynamicTable.tsx`.

**Tests:** `frontend/src/features/query/QueryWorkspace.test.tsx`, `frontend/src/components/DynamicTable.test.tsx`, `frontend/e2e/query-flow.spec.ts`.

**Interfaces:** tipos TypeScript refletem DTOs 2–6; `api` oferece CRUD/test/discovery/validate/execute; `DynamicTable` recebe `columns` e `rows`. Monaco usa SQL/PostgreSQL e arquivos locais do bundle, sem carregar SQL ou editor via terceiros.

- [ ] Escrever teste com respostas controladas: trocar conexão A→B enquanto discovery A atrasa mantém lista B; seleção antiga é limpa; erro mostra mensagem e preserva SQL. Executar `npm --prefix frontend test -- --run` e confirmar falha.
- [ ] Implementar tela administrativa em português: navegação Consultas/Conexões, cards de conexão, diálogo acessível Radix, estado vazio real e formulário que nunca preenche senha existente. Não carregar registros fictícios como se fossem resultados reais.
- [ ] Implementar seleção pesquisável, selecionar todos/limpar, contador, Monaco, validar/executar/limpar. AbortController ou identificador de request impede respostas antigas; execução captura seleção e SQL e mantém resultado identificado por essa execução.
- [ ] Escrever testes de tabela: labels duplicados renderizam ambos; null difere de vazio; paginação/ordenação/busca/filtro funcionam; copiar linha inclui todas as colunas visíveis. Confirmar falha e implementar TanStack Table com scroll e cabeçalho fixo.
- [ ] Implementar loading global, resumo, status por database, abas consolidadas/individuais, mensagens de erro e aviso de truncamento. Impedir execução sem conexão, seleção ou SQL.
- [ ] Executar testes, `npm --prefix frontend run build` e navegador com backend/PostgreSQL reais: cadastrar, editar sem senha, testar, descobrir, executar em três databases e verificar falha parcial. Verificar teclado, viewport desktop e estreita. Checkpoint/commit `feat: deliver QueryHub query workspace`.

### Task 8: CSV e XLSX do resultado exibido

**Files:** `service/ExportService.java`, `service/ExportValidationService.java`, `controller/ExportController.java`, `dto/ExportRequest.java`, `config/RequestBodyLimitFilter.java`, `frontend/src/features/query/ExportActions.tsx`.

**Tests:** `service/ExportServiceTest.java`, `controller/ExportControllerIT.java`; ampliar e2e frontend.

**Interfaces:** `ExportRequest` recebe snapshot `ExecutionResponse`; `ExportService.csv(ExportRequest,OutputStream):void`, `xlsx(ExportRequest,OutputStream):void`; POSTs exportam anexos com Content-Disposition e content-type correto. Reconstruir consolidado a partir de results validados para evitar inconsistência.

- [ ] Escrever CSV test com vírgula, aspas, quebra de linha, Unicode e fórmula `=1+1`: escaping válido e fórmula neutralizada; primeira coluna DATABASE. XLSX test abre workbook e verifica Resumo/Consolidado/individuais, nomes únicos <=31 caracteres, erros no resumo e zero células FORMULA.
- [ ] Escrever teste de payload adulterado: linhas fora do limite, mapas com chaves inexistentes, profundidade excessiva, body >67108864 bytes com e sem Content-Length retornam 400/413 e não abrem JDBC.
- [ ] Confirmar falhas; implementar limite de bytes na leitura do body, validação estrutural e orçamento antes da exportação. POI com streaming quando aplicável, fechamento de workbook/temporários mesmo se cliente desconectar. Validar nomes reservados, caracteres inválidos e colisões das abas sem distinguir caixa.
- [ ] Integrar botões CSV/Excel e baixar snapshot atual; informar erro sem apagar resultado.
- [ ] Executar `mvn -f backend/pom.xml verify`, testes/build frontend e download no navegador; esperado: mesmos registros exibidos, sem nova execução JDBC. Checkpoint/commit `feat: export query snapshots to CSV and XLSX`.

### Task 9: Auditoria, operação e aceite final

**Files:** `domain/audit/QueryAudit.java`, `repository/QueryAuditRepository.java`, `service/AuditService.java`, `backend/src/main/resources/db/migration/V2__query_audit.sql`, `README.md`, `docs/testing.md`, `docs/security.md`; ampliar `DynamicQueryService` e configuração Compose.

**Tests:** `service/AuditServiceIT.java`, `QueryHubAcceptanceIT.java`.

**Interfaces:** `AuditService.record(UUID,ConnectionSnapshot,String,DatabaseQueryResult):boolean` recebe SQL apenas para calcular SHA-256 UTF-8, não persiste texto; retorno false adiciona warning `AUDIT_UNAVAILABLE` à resposta. Cada gravação tem transação interna independente da consulta externa.

- [ ] Escrever teste: três databases produzem três eventos com mesmo executionId e fingerprint SHA-256; evento não contém SQL, resultados ou credenciais; excluir conexão mantém evento e snapshot do nome. Indisponibilidade simulada da auditoria preserva resultado e inclui warning.
- [ ] Confirmar falha e implementar migration, gravação e integração com serviço de execução. Erros auditados são códigos/mensagens controladas, não exception JDBC integral.
- [ ] Completar README com arquitetura, configuração, geração local da chave, backup, SSL/CA, privilégios PostgreSQL, limites, endpoints, execução Docker/local, testes e roadmap. Documentar search_path, funções aceitas, export de snapshot não autenticado, consistência entre databases e ausência de login.
- [ ] Rodar `mvn -f backend/pom.xml verify`, `npm --prefix frontend test -- --run`, `npm --prefix frontend run build` e `docker compose up --build` com segredos locais ignorados. Validar healthchecks, migrations e persistência após restart, sem remover volumes do usuário.
- [ ] Executar aceite no navegador: conexão→teste→discovery→seleção de três bases→SELECT→consolidado/abas→CSV/XLSX; testar escrita bloqueada, timeout, truncamento e falha parcial. Revisar logs/respostas para ausência de segredos e confirmar que nenhuma porta 8080 foi publicada no host.
- [ ] Fazer revisão final do diff e segurança; corrigir achados e repetir apenas os checks afetados. Registrar testes executados e limitações verificáveis em `docs/testing.md`. Checkpoint/commit `feat: audit executions and document verified MVP`.

## Cobertura e handoff

Spec 1–2 → tarefas 1 e 7; 3–5 → 2 e 9; 6–7 → 3; 8 → 4; 9 → 5; 10–12 → 3, 5 e 6; 13 → 2–8; 14 → 1 e 9; 15 → testes de todas as tarefas e revisão final. Exportação/auditoria seguem o primeiro aceite do fluxo principal. SSE, cancelamento público, queries salvas, histórico completo, autenticação e RBAC permanecem no roadmap.

Método recomendado: execução nativa nesta conversa, por tarefa, pois os nove entregáveis compartilham contratos sequenciais; revisão independente ao final. A implementação começa após revisão deste plano e escolha do método pelo usuário.
