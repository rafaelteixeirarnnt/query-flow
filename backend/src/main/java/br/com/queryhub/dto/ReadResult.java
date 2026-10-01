package br.com.queryhub.dto;

import jakarta.validation.constraints.*;
import java.util.*;

public record ReadResult(
    List<ColumnMetadata> columns,
    List<Map<String, Object>> rows,
    boolean truncated,
    String truncationReason) {}
