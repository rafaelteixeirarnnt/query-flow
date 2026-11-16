package br.com.queryhub.service;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

import br.com.queryhub.config.QueryProperties;
import br.com.queryhub.dto.*;
import java.sql.*;
import java.util.List;
import org.junit.jupiter.api.Test;

class DatabaseQueryRunnerTest {
  private final QueryProperties props =
      new QueryProperties(1, 1000, 1, 2, 50, 1, 1, 1048576, 33554432);

  @Test
  void preservesOriginalFailureWhenRollbackAndCloseAlsoFailAndReleasesPermit() throws Exception {
    var factory = mock(ExternalConnectionFactory.class);
    var connection = mock(Connection.class);
    var configuration = mock(Statement.class);
    var query = mock(Statement.class);
    var limiter = new ExternalConnectionLimiter(props);
    when(factory.open(null, "qa")).thenReturn(connection);
    when(connection.createStatement()).thenReturn(configuration, query);
    when(query.executeQuery("SELECT 1")).thenThrow(new SQLException("original timeout", "57014"));
    doThrow(new SQLException("connection already closed", "08003")).when(connection).rollback();
    doThrow(new SQLException("close failed", "08006")).when(connection).close();
    var runner = new DatabaseQueryRunner(factory, limiter, props, mock(ResultSetReader.class));
    var result = runner.run(null, "qa", "SELECT 1", new ExecutionContext(33554432, 1000));
    assertThat(result.error().code()).isEqualTo("QUERY_TIMEOUT");
    verify(query).close();
    verify(configuration).close();
    verify(connection).close();
    try (var permit = limiter.acquire()) {
      assertThat(permit).isNotNull();
    }
  }

  @Test
  void closesResultSetStatementAndConnectionOnSuccessfulTruncation() throws Exception {
    var factory = mock(ExternalConnectionFactory.class);
    var connection = mock(Connection.class);
    var configuration = mock(Statement.class);
    var query = mock(Statement.class);
    var rs = mock(ResultSet.class);
    var reader = mock(ResultSetReader.class);
    var context = new ExecutionContext(33554432, 1000);
    when(factory.open(null, "qa")).thenReturn(connection);
    when(connection.createStatement()).thenReturn(configuration, query);
    when(query.executeQuery("SELECT 1")).thenReturn(rs);
    when(reader.read(rs, context.budget, 1000))
        .thenReturn(new ReadResult(List.of(), List.of(), true, "BYTE_LIMIT"));
    var result =
        new DatabaseQueryRunner(factory, new ExternalConnectionLimiter(props), props, reader)
            .run(null, "qa", "SELECT 1", context);
    assertThat(result.status()).isEqualTo("SUCCESS");
    verify(rs).close();
    verify(query).close();
    verify(connection).rollback();
    verify(connection).close();
  }

  @Test
  void releasesPermitWhenOpeningConnectionFails() throws Exception {
    var factory = mock(ExternalConnectionFactory.class);
    var limiter = new ExternalConnectionLimiter(props);
    when(factory.open(null, "qa")).thenThrow(new SQLException("unavailable", "08001"));
    var runner = new DatabaseQueryRunner(factory, limiter, props, mock(ResultSetReader.class));
    for (int attempt = 0; attempt < 3; attempt++) {
      var result = runner.run(null, "qa", "SELECT 1", new ExecutionContext(33554432, 1000));
      assertThat(result.error().code()).isEqualTo("CONNECTION_FAILED");
      try (var permit = limiter.acquire()) {
        assertThat(permit).isNotNull();
      }
    }
  }

  @Test
  void closesAllResourcesWhenReadingRowsFails() throws Exception {
    var factory = mock(ExternalConnectionFactory.class);
    var connection = mock(Connection.class);
    var configuration = mock(Statement.class);
    var query = mock(Statement.class);
    var rs = mock(ResultSet.class);
    var reader = mock(ResultSetReader.class);
    var limiter = new ExternalConnectionLimiter(props);
    var context = new ExecutionContext(33554432, 1000);
    when(factory.open(null, "qa")).thenReturn(connection);
    when(connection.createStatement()).thenReturn(configuration, query);
    when(query.executeQuery("SELECT 1")).thenReturn(rs);
    when(reader.read(rs, context.budget, 1000))
        .thenThrow(new SQLException("stream interrupted", "08006"));
    var result = new DatabaseQueryRunner(factory, limiter, props, reader)
        .run(null, "qa", "SELECT 1", context);
    assertThat(result.error().code()).isEqualTo("CONNECTION_LOST");
    var order = inOrder(rs, query, connection);
    order.verify(rs).close();
    order.verify(query).close();
    order.verify(connection).rollback();
    order.verify(connection).close();
    try (var permit = limiter.acquire()) {
      assertThat(permit).isNotNull();
    }
  }

}
