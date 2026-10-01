package br.com.queryhub.dto;

import jakarta.validation.constraints.*;
import java.util.*;

public record ExecuteQueryRequest(
    @NotNull @Positive Long connectionId,
    @NotEmpty @Size(max = 50) List<@NotBlank @Size(max = 63) String> databases,
    @NotBlank @Size(max = 100000) String sql,
    @Positive
        @com.fasterxml.jackson.databind.annotation.JsonDeserialize(
            using = RowLimitDeserializer.class)
        Integer rowLimit) {
  public ExecuteQueryRequest(Long connectionId, List<String> databases, String sql) {
    this(connectionId, databases, sql, null);
  }
}
