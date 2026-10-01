package br.com.queryhub.service;

import br.com.queryhub.config.QueryProperties;
import br.com.queryhub.dto.*;
import br.com.queryhub.exception.ApiException;
import java.util.*;
import org.springframework.stereotype.Service;

@Service
public class ExportValidationService {
  private final QueryProperties props;

  public ExportValidationService(QueryProperties props) {
    this.props = props;
  }

  public void validate(ExportRequest request) {
    try {
      var e = request.execution();
      if (e == null
          || e.results() == null
          || e.results().size() > 50
          || e.connectionName() == null
          || e.connectionName().length() > 120
          || e.executedAt() == null) throw new IllegalArgumentException();
      long bytes = 0;
      Set<String> dbs = new HashSet<>();
      for (var r : e.results()) {
        if (r.database() == null
            || r.database().length() > 63
            || !dbs.add(r.database())
            || r.columns() == null
            || r.columns().size() > 256
            || r.rows() == null
            || r.rows().size() > props.maxResultRows()
            || !Set.of("SUCCESS", "ERROR").contains(r.status())
            || r.rowCount() != r.rows().size()) throw new IllegalArgumentException();
        if (!r.rows().isEmpty() && r.columns().isEmpty()) throw new IllegalArgumentException();
        bytes += 256L * r.columns().size() + 128L * r.rows().size();
        Set<String> keys = new HashSet<>();
        for (var c : r.columns()) {
          if (c.key() == null
              || c.key().length() > 64
              || !keys.add(c.key())
              || c.label() == null
              || c.label().length() > 256
              || c.typeName() == null
              || c.typeName().length() > 128) throw new IllegalArgumentException();
        }
        for (var row : r.rows()) {
          bytes += 128L * keys.size();
          if (bytes > 33554432) throw new IllegalArgumentException();
          if (!keys.equals(row.keySet())) throw new IllegalArgumentException();
          for (Object v : row.values()) {
            bytes += measure(v, 0) + 64;
            if (bytes > 33554432) throw new IllegalArgumentException();
          }
        }
        if (r.error() != null
            && (r.error().message() == null || r.error().message().length() > 1000))
          throw new IllegalArgumentException();
      }
    } catch (Exception ex) {
      throw new ApiException(
          400, "INVALID_EXPORT", "Resultado inválido ou excede os limites de exportação.");
    }
  }

  private long measure(Object v, int depth) {
    if (v == null) return 4;
    if (depth > 8) throw new IllegalArgumentException();
    if (v instanceof List<?> list) {
      if (list.size() > 10000) throw new IllegalArgumentException();
      long n = 0;
      for (Object x : list) n += measure(x, depth + 1);
      if (n > 1048576) throw new IllegalArgumentException();
      return n;
    }
    if (!(v instanceof String || v instanceof Number || v instanceof Boolean))
      throw new IllegalArgumentException();
    int n = v.toString().getBytes(java.nio.charset.StandardCharsets.UTF_8).length;
    if (n > 1048576) throw new IllegalArgumentException();
    return n;
  }
}
