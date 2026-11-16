# Validação do MVP — 4 de outubro de 2026

## Resultado e alcance

O código local passou pelos testes descritos abaixo. Não foi observado acúmulo de sessões externas nos cenários executados. Saturação e recuperação foram reproduzidas sem aumentar limites. Nenhum defeito funcional novo foi comprovado; esta rodada adicionou cobertura de testes e documentação, sem alterar código de produção.

Isso **não determina a causa histórica** dos relatos `08003`, `DATABASE_ERROR` e `CONNECTION_CAPACITY`: não foram acessados servidores reais, consultas originais ou seus logs. Não equivale a certificação para produção nem a teste prolongado sob falhas de rede.

## Ambiente e isolamento

- macOS ARM64; Maven; validação final com OpenJDK 21.0.1, compilação release 21. Uma rodada preliminar também passou em Java 25.0.1, o padrão da máquina.
- Backend: Spring Boot 3.5.16; PostgreSQL 17 Alpine descartável via Testcontainers, portas aleatórias e aplicação HTTP em porta aleatória.
- Frontend: dependências locais existentes, Vitest e build TypeScript/Vite; nenhuma dependência instalada ou atualizada.
- Aceite manual automatizado: Chrome pela ferramenta de navegador, `http://localhost:13000`, viewport normal de desktop, largura de 1512 px nas capturas.
- Ambiente adicional: rede `queryhub-stability-net`, containers `queryhub-stability-db`, `queryhub-stability-backend` e `queryhub-stability-frontend`. A porta HTTP foi publicada somente em `127.0.0.1`; PostgreSQL não foi publicado no host.
- JAR recém-compilado e `frontend/dist` montados somente para leitura nos containers de teste. Imagens de runtime já disponíveis; sem reconstrução da instalação principal.
- Banco interno `queryhub`; databases externos `demo_a`, `demo_b`, `demo_c`, cada um inicialmente com 1.205 linhas sintéticas `Item 1` a `Item 1205`. Usuário externo de leitura, sem superuser, com acesso somente aos fixtures necessários. Credenciais fictícias, não reproduzidas neste relatório.
- No aceite: limite padrão de 1.000 linhas, duas conexões externas, duas execuções simultâneas, admissão de 1 s e timeout de consulta de 2 s. Esses valores pertencem exclusivamente ao ambiente de teste.

## Comandos e resultados

```sh
# Rodada final do backend; selecionar um JDK 21 instalado.
JAVA_HOME="$(/usr/libexec/java_home -v 21)" \
DOCKER_HOST="unix://$HOME/.docker/run/docker.sock" \
mvn -f backend/pom.xml clean verify

npm --prefix frontend test -- --run
npm --prefix frontend run build
git diff --check
```

| Verificação | Resultado observado |
|---|---|
| Backend Java 21 | 57 testes unitários + 16 de integração; zero falhas, erros ou skips; BUILD SUCCESS |
| Frontend | 8 testes em 4 arquivos aprovados |
| Build frontend | TypeScript e Vite concluídos; aviso de chunk grande do Monaco, sem falha |
| Diff | Sem erros de whitespace |

Relatórios Maven são gerados em `backend/target/surefire-reports` e `backend/target/failsafe-reports`. Logs brutos ficaram fora do repositório, em `/tmp/queryhub-stability-*.log`; são temporários. Mockito emitiu aviso de carregamento dinâmico de agente, sem falhas de testes.

## Fechamento de recursos e capacidade

### Cobertura automatizada

Os testes existentes já cobriam falha original preservada quando rollback e close falham, fechamento de ResultSet/Statement/Connection após truncamento, consulta lenta seguida de recuperação, repetição de consulta/descoberta/teste de conexão, concorrência global e encerramento de transação abandonada pelo servidor.

Foram acrescentados quatro testes:

- `DatabaseQueryRunnerTest.releasesPermitWhenOpeningConnectionFails`: três tentativas com falha de abertura `08001`; a vaga pode ser adquirida novamente após cada falha.
- `DatabaseQueryRunnerTest.closesAllResourcesWhenReadingRowsFails`: falha de leitura `08006`, com fechamento ordenado de ResultSet, Statement, rollback e Connection, seguido de nova aquisição de vaga.
- `QueryHubAcceptanceIT.exhaustedConnectionCapacityRecoversAfterPermitsAreReleased`: ocupa deterministicamente as duas vagas do limitador, recebe HTTP 429 / `CONNECTION_CAPACITY`, libera as vagas e executa com sucesso em dois databases. A ocupação é feita pelo teste diretamente no limitador para evitar dependência de timing; o cenário com consultas reais também foi executado abaixo.
- `QueryHubAcceptanceIT.exportsUseSnapshotEvenAfterSourceTableIsRemoved`: consulta uma tabela sintética, remove a tabela e exporta CSV/XLSX com HTTP 200 e o valor original `123`. O CSV e a célula do workbook são inspecionados; não há dependência de reexecutar o SQL de origem.

`@AfterEach` da suíte de integração verifica que não restam sessões externas identificadas por `application_name='QueryHub'`, com espera limitada de até 3 s. O monitor usa outra conexão e outro application name. O teste de transação abandonada mantém deliberadamente o cliente aberto e verifica que a proteção do servidor encerra a sessão.

### Medição no PostgreSQL isolado

Amostra usada pelo monitor, via `docker exec queryhub-stability-db psql`:

```sql
SELECT count(*)
FROM pg_stat_activity
WHERE application_name = 'QueryHub';

SELECT application_name, datname, state, count(*)
FROM pg_stat_activity
WHERE backend_type = 'client backend' AND pid <> pg_backend_pid()
GROUP BY 1, 2, 3;
```

Resultados da rodada HTTP adicional:

1. Vinte execuções consecutivas, cada uma em três databases: 60 resultados bem-sucedidos. Contagem final de sessões externas em cada execução: vinte amostras iguais a **zero**.
2. Duas execuções simultâneas, cada uma consultando uma view sintética lenta: pico observado de **duas sessões externas**, conforme o limite configurado.
3. Enquanto ambas estavam ocupadas, uma terceira execução recebeu HTTP 429 / `EXECUTION_CAPACITY`; teste de conexão recebeu HTTP 429 / `CONNECTION_CAPACITY` após aguardar admissão.
4. As duas consultas lentas terminaram com `QUERY_TIMEOUT`. A execução seguinte funcionou nos três databases; contagem externa final: **zero**.
5. Após as operações, sobraram **cinco sessões `idle` no database interno `queryhub`**, com application name `PostgreSQL JDBC Driver`, compatíveis com o pool Hikari configurado. Elas não são sessões externas vazadas.

### Interpretação dos erros

- `CONNECTION_CAPACITY` identifica falta temporária de vagas locais; foi reproduzido legitimamente e recuperou após liberação.
- `EXECUTION_CAPACITY` limita requisições de execução simultâneas, antes de abrir seus workers.
- `SERVER_CONNECTION_CAPACITY` mapeia SQLSTATE `53300` do PostgreSQL. O mapeamento é testado; não foi esgotado o limite real do servidor nesta rodada.
- `08003` é mapeado para `CONNECTION_LOST` no código atual. Há testes de classificação e de limpeza, mas não foi reproduzida a sequência original relatada pelo usuário.
- As correções de try-with-resources, preservação da exceção original e timeout de transação abandonada já estavam no workspace antes desta rodada; foram preservadas e validadas, não são implementações novas desta tarefa.

## Aceite visual e HTTP

Fluxo exercitado: cadastrar conexão fictícia → testar → conectar/descobrir → selecionar três databases → escrever SQL → executar → consultar consolidado/abas → paginar → exportar → verificar falha parcial e timeout/recuperação.

| Cenário | Evidência |
|---|---|
| Identidade e carregamento | Título QueryHub, conteúdo e controles presentes; sem tela vazia ou overlay de erro |
| Cadastro e teste | Conexão `Validação isolada` salva; mensagem de conexão bem-sucedida |
| Descoberta | Três databases fictícios disponíveis e selecionáveis |
| Padrão 1000 | Campo inicia em 1000; consulta retornou 3.000 linhas e aviso de truncamento por database |
| Valor acima do padrão | Campo alterado para 1500; retornou as 1.205 linhas de cada database, 3.615 no total, sem truncamento |
| Origem e abas | Coluna DATABASE no consolidado; aba `demo_b` mostrou 1.205 registros |
| Paginação | Tabela de resultados com exatamente 100 linhas no DOM; página seguinte começou em `Item 101` |
| Rolagem interna | Contêiner de resultados com 370 px de altura e conteúdo de 4.971 px, overflow vertical automático |
| Falha parcial | Tabela renomeada apenas em `demo_c`: dois sucessos, um `TABLE_NOT_FOUND`, seis registros preservados; resumo exibiu o erro e os sucessos |
| Timeout | View com atraso de 4 s foi interrompida em aproximadamente 2,1 s; aba exibiu `QUERY_TIMEOUT` |
| Recuperação visual | `SELECT 1 AS recuperado` funcionou após o timeout |
| Exportação pelo navegador | Cliques em CSV e Excel produziram HTTP 200 no Nginx: 98.220 bytes e 6.155 bytes, respectivamente |
| Console | Nenhum erro ou aviso nos logs capturados da segunda aba de teste |

As capturas desta rodada ficaram fora do repositório: `/tmp/queryhub-stability-results.jpg` e `/tmp/queryhub-stability-partial.jpg`. As imagens públicas existentes em `docs/images` foram preservadas.

A espera automatizada pelo evento de download CSV expirou e desconectou a primeira aba. Uma nova aba permitiu continuar o aceite. O proxy confirmou a resposta CSV bem-sucedida; o clique em Excel também retornou HTTP 200. A conclusão do salvamento dos arquivos pelo Chrome **não foi confirmada pelo evento de download**. Conteúdo e exportação sem reexecução foram confirmados separadamente pelos testes de integração.

## Instalação principal versus código validado

Foram copiados somente os artefatos públicos de aplicação dos containers principais, sem leitura de variáveis de ambiente, credenciais ou bancos reais:

```sh
docker cp queryhub-queryhub-backend-1:/app/app.jar /tmp/queryhub-main-app.jar
docker cp queryhub-queryhub-frontend-1:/usr/share/nginx/html /tmp/queryhub-main-html
```

Comparação byte a byte após `clean verify` em Java 21: os **54 arquivos de classes/recursos em `BOOT-INF/classes`** coincidiram com `backend/target/classes`. Os 92 arquivos do build frontend coincidiram byte a byte com os servidos pelo container principal; a imagem contém adicionalmente `50x.html`, página padrão do Nginx fora do build da aplicação. Isso comprova correspondência do código da aplicação comparado, não igualdade das configurações, dados ou de todas as bibliotecas de runtime.

Os containers principais não foram reconstruídos, reiniciados ou alterados. A instalação manteve suas datas de início. Nenhum banco real foi consultado. README, imagens públicas e todas as alterações preexistentes fora dos dois arquivos de teste ampliados foram preservados.

## Limitações e próximos passos

- Não houve teste prolongado, partição de rede, encerramento abrupto do processo, nem reinício do servidor externo durante consulta. O fallback de transação abandonada tem teste, mas não cobre todo tipo de falha de rede.
- Esta rodada visual foi desktop/Chrome. Mobile, outros navegadores e captura conclusiva do download devem integrar uma próxima rodada de aceite.
- Paginação ainda é de visualização: todo o resultado solicitado chega numa resposta. O teste com 3.615 linhas não representa cargas de milhões de linhas.
- O aviso de bundle grande do Monaco permanece como oportunidade de desempenho, sem alteração de dependências ou escopo nesta tarefa.
- Não há autenticação/RBAC; o resultado não autoriza ampliar exposição da instalação.
- A proposta priorizada está em [Próximas etapas](next-steps.md). Nenhuma dessas funcionalidades foi implementada nesta rodada.

Os três containers e a rede de aceite temporários foram removidos ao final, incluindo somente o volume descartável criado para o PostgreSQL de teste. Testcontainers encerrou seus recursos automaticamente. Não houve commit, push, merge ou publicação.
