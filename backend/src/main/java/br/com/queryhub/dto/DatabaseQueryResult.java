package br.com.queryhub.dto;

import jakarta.validation.constraints.*;
import java.util.*;

public record DatabaseQueryResult(
    String database,
    String status,
    List<ColumnMetadata> columns,
    List<Map<String, Object>> rows,
    int rowCount,
    long executionTimeMs,
    boolean truncated,
    String truncationReason,
    QueryError error,
    int rowLimit) {}
