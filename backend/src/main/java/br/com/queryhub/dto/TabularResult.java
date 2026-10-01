package br.com.queryhub.dto;

import java.util.List;
import java.util.Map;

public record TabularResult(
    List<ColumnMetadata> columns, List<Map<String, Object>> rows, boolean truncated) {
  public TabularResult(List<ColumnMetadata> columns, List<Map<String, Object>> rows) {
    this(columns, rows, false);
  }
}
