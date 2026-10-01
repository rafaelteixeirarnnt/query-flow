# Limite configurável por database — 25/09/2026

## Mudança

A propriedade existente `query.max-result-rows` agora usa `${CONSULTA_LIMITE_REGISTROS:1000}`. A variável antiga `MAX_RESULT_ROWS` não é mais utilizada. O `.env` real foi preservado.

O valor 10000 estava no default de `application.yml`, `.env.example`, `docker-compose.yml`, README, no `@Max` de `QueryProperties` e na validação de quantidade de linhas de `ExportValidationService`. A execução JDBC já recebia o valor de `QueryProperties`.

`Statement.setMaxRows(limite + 1)` limita o cursor JDBC por database; `ResultSetReader` retém até o limite e usa a linha extra somente para detectar truncamento. Nenhuma lista completa de resultados é carregada para depois ser cortada. Exatamente o limite sem linha adicional não é truncamento. Limites existentes de memória continuam independentes.

A configuração usa Bean Validation `@Min(1)` e um teto técnico `Integer.MAX_VALUE - 1`, necessário ao `+1` do JDBC. Não há teto de negócio de 10000. O binding rejeita valores não inteiros. `DatabaseQueryResult.rowLimit` informa o limite tanto em sucesso como em erro; a interface usa esse campo no aviso, sem constante 1000. A exportação valida a quantidade pelo mesmo limite configurado.

## Arquivos alterados nesta solicitação

- `.env.example`, `docker-compose.yml`, `README.md`
- `backend/src/main/resources/application.yml`
- `backend/src/main/java/br/com/queryhub/config/QueryProperties.java`
- `backend/src/main/java/br/com/queryhub/dto/DatabaseQueryResult.java`
- `backend/src/main/java/br/com/queryhub/service/DatabaseQueryRunner.java`
- `backend/src/main/java/br/com/queryhub/service/ExportValidationService.java`
- `backend/src/test/java/br/com/queryhub/config/QueryPropertiesTest.java`
- `backend/src/test/java/br/com/queryhub/QueryHubAcceptanceIT.java`
- `backend/src/test/java/br/com/queryhub/service/ExportServiceTest.java`
- `backend/src/test/java/br/com/queryhub/service/ResultConsolidatorTest.java` (fixtures do DTO)
- `frontend/src/types/api.ts`
- `frontend/src/features/query/ExecutionResults.tsx`
- `frontend/src/features/query/ExecutionResults.test.tsx`
- `frontend/src/app/App.test.tsx` (remoção de opção `exact` inválida no matcher de teste para liberar o build)
- Este relatório, logs e capturas em `docs/design/queryhub-limite-*.png`.

## Verificações

- Oito casos de configuração: padrão 1000; variável 500, 5000 e 10001; falha ao iniciar com 0, -1, texto e fração. Antes da alteração, os oito falharam; após, passaram.
- Integração PostgreSQL real: abaixo, exatamente e acima do limite; múltiplos databases com teto individual e metadados `rowLimit`/`ROW_LIMIT`.
- Exportação aceita exatamente o limite e rejeita excesso para configuração 500 e 10001.
- Frontend verifica mensagem com `rowLimit=500` recebido do backend.
- 47 testes unitários Java e 11 de integração passaram. Seis testes frontend passaram.
- `mvn -f backend/pom.xml verify` e `npm run build` passaram. O Vite mantém aviso de tamanho do chunk Monaco, sem erro de build.
- Docker Compose reconstruído e iniciado sem a nova variável informada: 1500 linhas de origem em cada um de três databases retornaram 1000 cada (3000 no consolidado).
- Mesma imagem reiniciada com `CONSULTA_LIMITE_REGISTROS=500`, sem recompilar: retornou 500 por database (1500 no consolidado).
- Ambos os ambientes testados com uma linha, exatamente o limite e 1500 linhas. Truncamento apenas quando havia linha excedente. CSV e XLSX retornaram arquivos válidos nos dois cenários.
- Avisos de 1000 e 500 confirmados na interface real; console sem erros/avisos na verificação desktop. Capturas salvas.
- Serviço restaurado ao padrão 1000 ao concluir.

Evidências de respostas resumidas: `limite-1000.jsonl` e `limite-500.jsonl`. São dados sintéticos de QA; nenhuma consulta foi feita à conexão do usuário.

A checagem final de inicialização revelou que `wget http://localhost/` no container nginx tentava um endereço sem listener. O healthcheck do frontend passou a usar `127.0.0.1`, validado com o mesmo comando dentro do container. Nenhuma regra funcional foi alterada. A conexão e o container de QA foram removidos após os testes; a conexão existente do usuário foi preservada.
