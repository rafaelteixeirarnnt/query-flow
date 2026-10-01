# QueryHub

> **Status: versão em desenvolvimento (working / work in progress).** Esta publicação disponibiliza o estado atual do projeto para avaliação e evolução. Ainda há ajustes a serem realizados, e o comportamento e a interface podem mudar nas próximas versões. Não é uma versão final ou estável.

Gerenciador de conexões e consultas PostgreSQL em múltiplos databases. Cadastre o servidor uma vez, descubra os databases, execute um SELECT/WITH de leitura e veja os resultados consolidados ou separados por database. Uma falha individual não descarta as consultas bem-sucedidas.

## Início rápido com Docker

Requisitos: Docker Engine/Desktop com Compose v2, acesso aos servidores PostgreSQL de destino e portas locais 3000, 8001 e 5433 disponíveis.

```sh
cp .env.example .env
openssl rand -base64 32
```

Coloque a chave gerada em `APP_ENCRYPTION_KEY` no `.env`, defina uma senha forte em `DB_PASSWORD` e execute:

```sh
docker compose up --build -d
docker compose ps
```

Abra [QueryHub](http://localhost:3000). Backend: `http://localhost:8001`; health: `http://localhost:8001/actuator/health`. Todas as portas são vinculadas a `127.0.0.1`; nenhuma porta 8080 é publicada no host. O banco interno usa volume persistente. `docker compose down` preserva o volume; **não use `down -v` se deseja manter os dados**.

O `.env` é ignorado e nunca deve ser versionado. Caso o ambiente já contenha um `.env` gerado durante a instalação, preserve sua chave: trocá-la sem recriptografar as senhas torna as conexões existentes inutilizáveis.

## Fluxo de uso

1. Em **Conexões**, clique em **Nova conexão** e informe host, porta, database inicial, usuário e senha.
2. Clique em **Testar** e depois em **Conectar**.
3. Selecione os databases descobertos. A busca permite selecionar os itens filtrados.
4. Escreva a consulta no Monaco, valide e execute.
5. Consulte o resumo e as abas por database. A aba **Consolidado** inclui DATABASE como primeira coluna.
6. Pesquise, filtre, ordene e pagine os resultados; clique em uma célula ou no botão de cópia da linha para copiar.
7. Baixe CSV ou Excel com os dados da execução exibida, sem executar novamente.

```sql
SELECT id, nome, situacao
FROM agh.exemplo
WHERE situacao = 'A';
```

Qualifique as tabelas pelo schema (`public.exemplo`, `agh.exemplo`): o `search_path` externo é restrito a `pg_catalog` para impedir resolução de funções em schemas de usuário. O database deve estar na lista descoberta pelo backend; nomes enviados pelo cliente são revalidados antes de abrir os workers.

## Arquitetura

- **Backend:** Java 21, Spring Boot 3.5.x, Web, Validation, Data JPA, Flyway, Actuator, PostgreSQL JDBC, JSqlParser e Apache POI. Maven compila com release 21.
- **Banco interno:** PostgreSQL; persiste conexões criptografadas e eventos de auditoria. JPA e HikariCP são usados apenas aqui.
- **Bancos externos:** JDBC direto, conexão por tarefa, transação read-only, timeout e fechamento com try-with-resources. Sem entidades JPA para resultados.
- **Frontend:** React/TypeScript/Vite, Monaco local (sem CDN), Radix Dialog, TanStack Table e Lucide.
- **Docker:** builds multi-stage, backend Java 21 sem usuário root, Nginx com proxy `/api`, healthchecks e dependência do PostgreSQL saudável antes das migrations.

```text
backend/src/main/java/br/com/queryhub/
  config/             Limites, propriedades e proteção de entrada
  controller/         REST e DTOs de resposta
  domain/             Persistência de conexão e auditoria
  dto/                Contratos públicos e snapshot interno
  repository/         Repositórios do PostgreSQL interno
  service/            Criptografia, descoberta, política SQL, execução e exportação
  util/               Conversão JDBC e erros sanitizados
backend/src/main/resources/db/migration/
frontend/src/
  app/                Shell e estilo
  features/connections/
  features/query/
  components/         Tabela dinâmica
  lib/                Cliente HTTP
  types/              Contratos TypeScript
```

## Configuração

| Variável | Padrão | Uso |
|---|---:|---|
| DB_HOST / DB_PORT | localhost / 5433 | Banco interno em execução local; Compose usa serviço e porta internos |
| DB_NAME / DB_USERNAME | queryhub | Banco interno |
| DB_PASSWORD | obrigatório | Senha do banco interno |
| APP_ENCRYPTION_KEY | obrigatório | 32 bytes em Base64 |
| QUERY_TIMEOUT_SECONDS | 60 | Timeout por consulta; máximo 300 |
| CONSULTA_LIMITE_REGISTROS | 1000 | Quantidade máxima de registros retornados por database em uma execução de consulta. |
| MAX_CONCURRENT_DATABASES | 5 | Conexões externas globais por instância |
| MAX_ACTIVE_EXECUTIONS | 2 | Requisições de execução admitidas simultaneamente |
| MAX_DATABASES_PER_QUERY | 50 | Quantidade por requisição |
| ADMISSION_TIMEOUT_SECONDS | 5 | Espera por uma conexão externa disponível |
| CONNECT_TIMEOUT_SECONDS | 10 | Estabelecimento de conexão |
| MAX_CELL_BYTES | 1048576 | Limite de uma célula após conversão |
| MAX_EXECUTION_BYTES | 33554432 | Orçamento conservador agregado dos resultados |

Também há limite de 100.000 caracteres SQL, 256 colunas, 1 MiB no body de requisições comuns e 64 MiB no body de exportação. O Compose limita a memória do backend a 768 MiB. Não há fila de requisições ilimitada: saturação global devolve HTTP 429; espera por conexão excedida em um worker vira erro individual. Limites se aplicam por instância, não globalmente entre réplicas.

O limite é lido por `query.max-result-rows` de `CONSULTA_LIMITE_REGISTROS` (padrão 1000). Para usar 5000, defina `CONSULTA_LIMITE_REGISTROS=5000` no ambiente ou `.env` e recrie o serviço (`docker compose up -d queryhub-backend`); não é necessário recompilar. Valores zero, negativos, fracionários ou não numéricos impedem a inicialização pelo binding/Bean Validation. O máximo técnico é 2147483646 para reservar uma linha adicional sem overflow do inteiro JDBC.

O JDBC aplica `Statement.setMaxRows(limite + 1)` por database, mantendo o cursor/fetch em lotes. A resposta contém `rowLimit` em cada resultado; o frontend usa esse valor no aviso. A exportação usa a mesma configuração para validar a quantidade de linhas. Não há teto de registros compartilhado entre databases; os limites de memória existentes continuam independentes.

Uma linha adicional detecta se houve truncamento: exatamente o limite de linhas não produz falso aviso. Excesso de bytes agregados marca `truncated` com `BYTE_LIMIT`; célula excessiva gera erro individual. Campos podem ser materializados pelo driver antes da checagem, portanto a limitação de memória do processo e consultas cuidadosas continuam necessárias.

## Criptografia e segurança

Senhas externas são protegidas por AES-256-GCM, nonce aleatório de 12 bytes, tag de 128 bits e AAD fixo versionado. A chave vem apenas do ambiente; não fica no banco. O envelope `v1:nonce:ciphertext` não é retornado pela API. A senha só é descriptografada para abrir JDBC, usando propriedades separadas da URL. PUT com senha omitida ou vazia preserva a anterior.

Guarde backup da chave separado do backup do PostgreSQL. Perder a chave exige recadastrar as senhas. Rotação não é automática: requer migração que leia com a chave anterior e grave com a nova, em ambiente controlado.

O MVP **não tem autenticação nem RBAC** e é destinado ao uso local/rede controlada. Quem acessar o serviço consegue administrar conexões e consultar dados. Não exponha as portas à Internet. Para uso compartilhado, coloque autenticação/TLS no proxy e restrinja acesso de rede e saída do backend. Não há bloqueio universal de destinos privados, pois são justamente os destinos esperados para as bases internas.

Use usuário PostgreSQL **sem superuser**, sem privilégios de escrita/DDL e sem acesso a funções perigosas. AST + read-only não são uma sandbox contra funções executadas em views, extensões ou objetos administrados pelo servidor de destino. Detalhes: [segurança](docs/security.md).

SSL habilitado usa `verify-full`, validando certificado e hostname. Para CA privada, monte o certificado no caminho confiável do driver pgJDBC para o usuário do container (`~/.postgresql/root.crt`) ou configure o truststore/certificado conforme sua implantação; não substitua por `sslmode=require` para ignorar a verificação. Sem SSL, as credenciais trafegam sem criptografia na conexão externa.

## Política SQL

A validação usa AST JSqlParser 5.3, aceita apenas uma instrução SELECT, incluindo CTEs SELECT, subconsultas, joins e operações de conjuntos. A política percorre recursivamente os objetos do AST, aceita uma lista explícita de tipos e rejeita nós desconhecidos. Não há fallback permissivo por regex.

Bloqueia DML/DDL, CTEs modificadoras, múltiplas instruções, SELECT INTO, locks, procedimentos e funções fora da lista. O subconjunto é deliberadamente restrito: casts explícitos, funções de janela e algumas construções específicas PostgreSQL ainda podem ser rejeitados. A validação não consulta tabelas nem garante que o SQL seja válido para a versão de cada servidor.

Funções permitidas: `count`, `sum`, `avg`, `min`, `max`, `lower`, `upper`, `length`, `trim`, `btrim`, `ltrim`, `rtrim`, `coalesce`, `nullif`, `date_trunc`, `to_char`, `round`, `abs`, `substring`, `substr`, `replace`, `concat`, `concat_ws`, `greatest`, `least`, `now`, `current_database`, `version`, `string_agg`, `array_agg`. A sintaxe correspondente também precisa pertencer ao subconjunto AST suportado. Funções qualificadas por schema são rejeitadas.

## Contratos REST

| Método | Rota | Finalidade |
|---|---|---|
| POST / GET | `/api/connections` | Criar / listar |
| GET / PUT / DELETE | `/api/connections/{id}` | Obter / editar / excluir |
| POST | `/api/connections/{id}/test` | Testar e medir tempo |
| GET | `/api/connections/{id}/databases` | Descobrir databases |
| POST | `/api/query/validate` | Validar `{ "sql": "SELECT 1" }` |
| POST | `/api/query/execute` | Executar em vários databases |
| POST | `/api/query/export/csv` | Exportar snapshot |
| POST | `/api/query/export/xlsx` | Exportar snapshot |

Criação/edição: `name`, `description`, `host`, `port`, `initialDatabase`, `username`, `password`, `sslEnabled`, `active`. Respostas nunca incluem senha. Campos inválidos recebem 400 sem ecoar valores sensíveis.

```json
{
  "connectionId": 1,
  "databases": ["database_hcac", "database_campo_limpo", "database_zaio"],
  "sql": "SELECT id, nome FROM agh.exemplo"
}
```

A execução retorna `executionId`, conexão, instante, duração, `summary`, `results`, `consolidated` e `warnings`. Cada resultado contém `database`, `status`, `columns`, `rows`, `rowCount`, `executionTimeMs`, `truncated`, `truncationReason` e `error`. Uma execução com falha parcial retorna HTTP 200; erros globais usam ProblemDetail com `code` e `detail` sanitizados.

As colunas possuem `key`, `label`, `jdbcType`, `typeName` e `sourceTypes`; linhas usam as chaves, preservando labels duplicados. A chave de origem é `__database`. Bigint/numeric são strings para preservar precisão; NULL é null, datas são strings, bytea é Base64 e arrays são listas. A consolidação unifica label+ocorrência; tipos divergentes ficam explícitos e colunas ausentes viram null. Não existe snapshot transacional único entre databases.

Exportações recebem `{ "execution": <resposta de execução> }`, validam o snapshot e reconstroem o consolidado. O arquivo não é prova autenticada da auditoria. CSV tem UTF-8/BOM, escaping e neutralização de fórmulas. Excel contém Resumo, Consolidado e abas individuais, com nomes válidos/únicos. Valores são escritos como texto, preservando identificadores longos e evitando fórmulas; NULL é célula vazia. Uma célula acima de 32.767 caracteres impede XLSX com erro explícito; CSV continua sendo a alternativa.

## Auditoria

Cada database recebe um evento com executionId, conexão, database, instante, status, duração, quantidade de linhas, SHA-256 do SQL e erro sanitizado. O SQL original, credenciais e resultados não são persistidos. Excluir uma conexão mantém seus eventos com referência anulada. Se a auditoria falhar, resultados são preservados e `AUDIT_UNAVAILABLE` aparece no frontend.

## Desenvolvimento e testes

Requisitos locais: Java **21**, Maven 3.9+, Node 22+, Docker para Testcontainers.

```sh
# Banco interno apenas
docker compose up -d queryhub-postgres
# Carregar o .env local controlado e iniciar backend em porta permitida
set -a
. ./.env
set +a
mvn -f backend/pom.xml spring-boot:run -Dspring-boot.run.arguments=--server.port=8001
```

Em outro terminal:

```sh
npm --prefix frontend ci
npm --prefix frontend run dev
```

Não execute o frontend Docker e Vite na mesma porta ao mesmo tempo.

```sh
mvn -f backend/pom.xml verify
npm --prefix frontend test -- --run
npm --prefix frontend run build
```

Testcontainers cria PostgreSQL isolado, usuário de leitura e três databases com diferenças de schema. Os testes cobrem credenciais, CRUD, descoberta, SQL bloqueado, falhas parciais, timeout/recuperação, limite exato/excedido, resultado vazio, colunas duplicadas, auditoria e exports. O projeto fixa a API Docker de testes em 1.44 para compatibilidade com Docker Desktop 29. Use `DOCKER_HOST=unix://$HOME/.docker/run/docker.sock` se a autodetecção do socket falhar no macOS. Se Buildx negar acesso ao seu cache, use `BUILDX_CONFIG=/tmp/queryhub-buildx docker compose up --build -d` sem alterar permissões globais.

## Roadmap

Após estabilizar o fluxo principal: queries salvas, favoritos, parâmetros, histórico com política de retenção, comparação de dados/schemas, progresso SSE, cancelamento público, autenticação/RBAC, auditoria por usuário, relatórios e agendamentos. Outros SGBDs não fazem parte deste MVP.

### Quantidade de registros por execução

No editor, o campo **Registros por database** permite informar de 1 até o máximo configurado no servidor. O valor inicial vem de `GET /api/query/config` (`maxResultRows`), sem constante no frontend. A quantidade de linhas por página da tabela apenas organiza a visualização.

`POST /api/query/execute` aceita o campo opcional `rowLimit` (inteiro positivo até `CONSULTA_LIMITE_REGISTROS`). Quando omitido, usa o máximo configurado, preservando clientes existentes. Valores inválidos ou acima do máximo retornam HTTP 400. O valor é isolado por execução e aplicado igualmente a cada database, tanto no JDBC quanto na leitura e na informação `rowLimit` da resposta.

Resultados com muitas colunas usam rolagem interna; a altura da tabela acompanha a janela até 420 px, com cabeçalho fixo e paginação fora da área rolável.
