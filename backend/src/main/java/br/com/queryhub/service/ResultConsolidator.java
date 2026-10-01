package br.com.queryhub.service;

import br.com.queryhub.dto.*;
import java.sql.Types;
import java.util.*;
import org.springframework.stereotype.Component;

@Component
public class ResultConsolidator {
  private record Identity(String label, int occurrence) {}

  public TabularResult consolidate(List<DatabaseQueryResult> results) {
    Map<Identity, String> keys = new LinkedHashMap<>();
    Map<Identity, LinkedHashSet<String>> types = new LinkedHashMap<>();
    Map<Identity, ColumnMetadata> first = new LinkedHashMap<>();
    for (var r : results) {
      Map<String, Integer> occurrences = new HashMap<>();
      for (var c : r.columns()) {
        var id = new Identity(c.label(), occurrences.merge(c.label(), 1, Integer::sum));
        keys.computeIfAbsent(id, k -> "c" + (keys.size() + 1));
        types.computeIfAbsent(id, k -> new LinkedHashSet<>()).add(c.typeName());
        first.putIfAbsent(id, c);
      }
    }
    var cols = new ArrayList<ColumnMetadata>();
    cols.add(new ColumnMetadata("__database", "DATABASE", Types.VARCHAR, "text", List.of("text")));
    keys.forEach(
        (id, key) -> {
          var c = first.get(id);
          cols.add(
              new ColumnMetadata(
                  key,
                  c.label(),
                  c.jdbcType(),
                  types.get(id).size() > 1 ? "mixed" : c.typeName(),
                  List.copyOf(types.get(id))));
        });
    var rows = new ArrayList<Map<String, Object>>();
    var budget = new ExecutionBudget(33554432);
    // Account for null-fill, keys and serialized data before allocating each output map.
    for (var c : cols) {
      if (!budget.reserve(256L + c.label().length() * 6L + c.typeName().length() * 6L))
        return new TabularResult(cols, rows, true);
    }
    for (var r : results) {
      Map<String, Integer> occ = new HashMap<>();
      Map<String, String> mapping = new LinkedHashMap<>();
      for (var c : r.columns())
        mapping.put(
            c.key(), keys.get(new Identity(c.label(), occ.merge(c.label(), 1, Integer::sum))));
      for (var source : r.rows()) {
        long rowBytes = 128L + cols.size() * 128L;
        for (Object value : source.values())
          if (value != null)
            rowBytes +=
                value.toString().getBytes(java.nio.charset.StandardCharsets.UTF_8).length * 6L;
        if (!budget.reserve(rowBytes)) return new TabularResult(cols, rows, true);
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("__database", r.database());
        keys.values().forEach(k -> row.put(k, null));
        mapping.forEach((a, b) -> row.put(b, source.get(a)));
        rows.add(row);
      }
    }
    return new TabularResult(cols, rows);
  }
}
