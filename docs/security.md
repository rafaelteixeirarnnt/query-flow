# Segurança e limites do MVP

- API sem autenticação: exposição local padrão, acesso ao Docker/host controlado. Uma instalação compartilhada precisa de autenticação externa, TLS e autorização apropriada.
- Não utilizar credencial PostgreSQL superuser. Recomenda-se usuário exclusivo com CONNECT, USAGE nos schemas de leitura e SELECT nas tabelas necessárias; revogue EXECUTE em funções perigosas conforme sua política.
- Host configurável implica acesso às redes do backend. Firewall/egress deve restringir servidores autorizados. Nunca exponha a API de cadastro irrestritamente.
- AES-GCM protege credenciais em repouso no banco, não contra alguém que controle o processo/host e sua chave de ambiente. Não há rotação ou cofre externo no MVP.
- A política de AST é uma allowlist para a versão fixada do parser. Alterar dependência/política requer repetir testes de bypass. A execução mantém transação read-only e search_path restrito.
- Views, operadores, tipos e funções instalados no servidor podem ter efeitos que a aplicação não consegue inferir de um SELECT. Read-only bloqueia escrita normal em tabelas persistentes, mas não transforma um servidor não confiável em sandbox. Restrinja funções e extensões no servidor.
- Respostas de erro não incluem SQLException.getMessage, stacktrace, URL JDBC, SQL ou senha. Não habilite log de bodies/SQL nem traces de credenciais no proxy/driver.
- Limite por célula é aplicado após conversão JDBC; o driver pode alocar campos grandes antes disso. O heap/container deve continuar limitado e consultas devem selecionar apenas dados necessários.
- Exportação recebe snapshot do cliente, não prova autoria/integridade. Valida formato/limites, neutraliza fórmulas CSV e usa strings XLSX. Dados exportados deixam a aplicação e devem ser tratados conforme sua política.
- Sem cancelamento público/progresso individual em tempo real no MVP. Timeouts e fechamento de recursos limitam consultas; queda do navegador não garante cancelamento imediato no servidor.
- Auditoria não contém SQL completo e não é imutável/assinada. Administradores do banco interno podem alterá-la. Defina retenção e backup antes de usar em produção.
