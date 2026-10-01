package br.com.queryhub.service;

import br.com.queryhub.config.QueryProperties;
import br.com.queryhub.dto.*;
import br.com.queryhub.exception.ApiException;
import br.com.queryhub.util.JdbcValueConverter;
import java.sql.*;
import java.util.*;
import org.springframework.stereotype.Component;

@Component
public class ResultSetReader {
  private final QueryProperties props;

  public ResultSetReader(QueryProperties p) {
    props = p;
  }

  public ReadResult read(ResultSet rs, ExecutionBudget budget, int rowLimit) throws SQLException {
    var meta = rs.getMetaData();
    int n = meta.getColumnCount();
    if (n > 256)
      throw new ApiException(400, "RESULT_TOO_WIDE", "Limite de 256 colunas por consulta.");
    var cols = new ArrayList<ColumnMetadata>();
    for (int i = 1; i <= n; i++)
      cols.add(
          new ColumnMetadata(
              "c" + i,
              meta.getColumnLabel(i),
              meta.getColumnType(i),
              meta.getColumnTypeName(i),
              List.of(meta.getColumnTypeName(i))));
    var rows = new ArrayList<Map<String, Object>>();
    boolean truncated = false;
    String reason = null;
    while (rs.next()) {
      if (rows.size() >= rowLimit) {
        truncated = true;
        reason = "ROW_LIMIT";
        break;
      }
      if (!budget.reserve(128L + n * 128L)) {
        truncated = true;
        reason = "BYTE_LIMIT";
        break;
      }
      Map<String, Object> row = new LinkedHashMap<>();
      for (int i = 1; i <= n; i++) {
        Object v = JdbcValueConverter.convert(rs.getObject(i), props.maxCellBytes());
        long bytes =
            v == null
                ? 4
                : v.toString().getBytes(java.nio.charset.StandardCharsets.UTF_8).length * 6L;
        if (!budget.reserve(bytes)) {
          truncated = true;
          reason = "BYTE_LIMIT";
          break;
        }
        row.put("c" + i, v);
      }
      if (truncated) break;
      rows.add(row);
    }
    return new ReadResult(cols, rows, truncated, reason);
  }
}
