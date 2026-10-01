package br.com.queryhub.dto;

import jakarta.validation.constraints.*;
import java.time.Instant;
import java.util.*;

public record ExecutionResponse(
    UUID executionId,
    long connectionId,
    String connectionName,
    Instant executedAt,
    long executionTimeMs,
    ExecutionSummary summary,
    List<DatabaseQueryResult> results,
    TabularResult consolidated,
    List<String> warnings) {}
