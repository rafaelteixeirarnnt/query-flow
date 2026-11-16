# Próximas etapas do QueryHub

Proposta após a [validação do MVP em 4 de outubro de 2026](testing.md). Esta lista não representa funcionalidades implementadas nem autorização para exposição pública.

## Prioridade 0 — fechar o aceite operacional

**Problema:** os testes locais não explicam retrospectivamente o erro original e não cobrem todas as interrupções de rede.

**Escopo mínimo:** confirmar downloads CSV/XLSX no navegador, executar ensaio prolongado em fixtures, interromper conexões/rede apenas no ambiente descartável e conferir recuperação. Registrar versão de aplicação e configuração sem segredos para futuras investigações.

**Dependências:** ambiente isolado reproduzível, monitor de sessões e limites explicitamente definidos. Investigar ocorrências reais somente mediante autorização específica de acesso aos dados/logs envolvidos.

**Aceite:** sessões externas voltam ao baseline após operações concluídas; limite de concorrência respeitado; consultas seguintes funcionam; erros preservam causa e não expõem credenciais; arquivos baixados abrem com os dados esperados. Resultados e limites documentados, sem inferir vazamento a partir de saturação isolada.

**Aplicação:** necessário tanto no uso local quanto no compartilhado antes de considerar uma versão estável.

## Prioridade 1 — cancelamento e progresso por database

**Problema:** usuário aguarda conclusão global ou timeout sem cancelar explicitamente nem acompanhar cada database.

**Escopo mínimo:** identificar a execução antes da conclusão, expor progresso por database e cancelamento idempotente; manter resultados já concluídos. Definir estados pendente, executando, sucesso, erro e cancelado e comportamento após desconexão/reconexão do cliente.

**Dependências:** ciclo de vida de execução, registro de statements ativos e contrato de eventos/polling; definição de retenção e limpeza. Para acesso compartilhado, associar execução ao usuário e autorizar leitura/cancelamento.

**Aceite:** cancelar uma consulta lenta libera recursos e vagas; repetir cancelamento não falha nem afeta outra execução; resultados concluídos permanecem acessíveis; progresso encerra em estado terminal; nova consulta funciona após cancelamento. Cobrir corrida entre conclusão e cancelamento e interrupção do cliente.

**Aplicação:** próxima melhoria funcional para uso local. No compartilhado, depende das permissões descritas abaixo.

## Prioridade 2 — resultados grandes com carregamento incremental

**Problema:** a paginação atual limita o DOM, mas não o volume transferido e mantido em memória.

**Escopo mínimo:** paginar um resultado materializado e limitado no servidor, ou outra estratégia explicitamente definida, com limite de armazenamento, expiração e limpeza. Definir ordenação, filtros e exportação sobre o mesmo resultado sem reexecutar silenciosamente o SQL. Evitar manter transações externas abertas enquanto o usuário navega.

**Dependências:** identificador de execução, armazenamento temporário, orçamento de recursos, política de expiração; autorização por usuário antes de uso compartilhado.

**Aceite:** primeira página chega sem transferir todo o resultado ao navegador; memória e armazenamento ficam dentro dos orçamentos; páginas não duplicam/perdem linhas do snapshot; navegação e exportação mantêm origem DATABASE; expiração é explícita; recursos externos são fechados independentemente da navegação. Medir dataset grande sintético e cliente lento.

**Aplicação:** benefício local e compartilhado; priorizar antes de incentivar consultas volumosas.

## Prioridade obrigatória antes de compartilhar — identidade e permissões

**Problema:** qualquer pessoa com acesso atual ao serviço pode administrar conexões e consultar dados.

**Escopo mínimo:** autenticação, autorização no backend para administrar conexões, consultar, exportar e acessar execuções; escopo de conexões por usuário/grupo; auditoria com identidade. Definir expiração de sessão, retenção, TLS, backup da chave e restrição de destinos de rede.

**Dependências:** escolha do provedor de identidade e modelo de acesso; migrations e política de auditoria. Um proxy autenticado sozinho não substitui autorização por recurso.

**Aceite:** requisições anônimas são recusadas; usuário não acessa conexão, resultado ou exportação alheios manipulando IDs; auditoria registra ator e ação sem senhas/resultados; logout/expiração funcionam; testes cobrem escalada de privilégios e acesso direto à API.

**Aplicação:** pode aguardar no MVP estritamente local/controlado; torna-se bloqueador para ampliar acesso e passa à frente das prioridades funcionais quando houver esse objetivo.

## Prioridade 3 — consultas salvas, favoritos, parâmetros e histórico

**Problema:** repetir trabalho exige copiar SQL e reconstruir contexto manualmente.

**Escopo mínimo:** salvar consulta nomeada, marcar favorito, editar e excluir; parâmetros tipados vinculados via JDBC; histórico mínimo de execução com retenção. Separar consulta salva de resultados e decidir explicitamente se SQL pode conter valores sensíveis.

**Dependências:** persistência/migrations, validação AST compatível com parâmetros, política de retenção e backup; propriedade e compartilhamento dependem da identidade no uso multiusuário.

**Aceite:** consultas persistem após reinício; parâmetros não permitem injeção nem substituição textual arbitrária de identificadores; favoritos e histórico respeitam permissões; expiração/exclusão seguem a política; nenhuma credencial é persistida em SQL ou histórico por conveniência.

**Aplicação:** produtividade local após estabilização; no compartilhado, somente com identidade, permissões e retenção definidas.
