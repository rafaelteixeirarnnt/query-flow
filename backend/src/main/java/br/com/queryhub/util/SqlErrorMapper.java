package br.com.queryhub.util;

import br.com.queryhub.dto.QueryError;
import java.sql.SQLException;

public final class SqlErrorMapper {
  private SqlErrorMapper() {}

  public static QueryError map(SQLException e) {
    String s = e.getSQLState();
    if (s == null) return new QueryError("DATABASE_ERROR", "Falha de comunicação com o database.");
    return switch (s) {
      case "28P01", "28000" -> new QueryError("INVALID_CREDENTIALS", "Credenciais inválidas.");
      case "42P01" -> new QueryError("TABLE_NOT_FOUND", "Tabela ou relação não encontrada.");
      case "42703" -> new QueryError("COLUMN_NOT_FOUND", "Coluna não encontrada.");
      case "57014" -> new QueryError("QUERY_TIMEOUT", "Consulta cancelada por timeout.");
      case "42501" -> new QueryError("PERMISSION_DENIED", "Permissão insuficiente.");
      case "3D000" -> new QueryError("DATABASE_NOT_FOUND", "Database não encontrado.");
      case "25006" ->
          new QueryError("READ_ONLY", "Operação bloqueada pela transação somente leitura.");
      case "42601" -> new QueryError("INVALID_SQL", "Sintaxe SQL inválida para este servidor.");
      default ->
          new QueryError(
              "DATABASE_ERROR",
              "Falha no database (SQLSTATE "
                  + (s.matches("[A-Z0-9]{5}") ? s : "desconhecido")
                  + ").");
    };
  }
}
