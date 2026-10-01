package br.com.queryhub.service;

import br.com.queryhub.config.QueryProperties;
import br.com.queryhub.dto.*;
import br.com.queryhub.exception.ApiException;
import br.com.queryhub.util.SqlErrorMapper;
import java.sql.*;
import java.util.*;
import org.springframework.stereotype.Service;

@Service
public class DatabaseQueryRunner {
  private final ExternalConnectionFactory factory;
  private final ExternalConnectionLimiter limiter;
  private final QueryProperties props;
  private final ResultSetReader reader;

  public DatabaseQueryRunner(
      ExternalConnectionFactory f,
      ExternalConnectionLimiter l,
      QueryProperties p,
      ResultSetReader r) {
    factory = f;
    limiter = l;
    props = p;
    reader = r;
  }

  public DatabaseQueryResult run(
      ConnectionSnapshot snapshot, String database, String sql, ExecutionContext context) {
    long start = System.nanoTime();
    try (var permit = limiter.acquire();
        var c = factory.open(snapshot, database)) {
      c.setReadOnly(true);
      c.setAutoCommit(false);
      try (var config = c.createStatement()) {
        config.setQueryTimeout(props.timeoutSeconds());
        config.execute("SET LOCAL search_path = pg_catalog");
        config.execute(
            "SET LOCAL statement_timeout = '" + (props.timeoutSeconds() * 1000L) + "ms'");
        config.execute("SET LOCAL lock_timeout = '" + (props.timeoutSeconds() * 1000L) + "ms'");
      }
      try (var s = c.createStatement()) {
        s.setQueryTimeout(props.timeoutSeconds());
        s.setFetchSize(200);
        s.setMaxRows(context.rowLimit + 1);
        context.register(s);
        try (var rs = s.executeQuery(sql)) {
          var r = reader.read(rs, context.budget, context.rowLimit);
          return new DatabaseQueryResult(
              database,
              "SUCCESS",
              r.columns(),
              r.rows(),
              r.rows().size(),
              elapsed(start),
              r.truncated(),
              r.truncationReason(),
              null,
              context.rowLimit);
        } finally {
          context.unregister(s);
          c.rollback();
        }
      }
    } catch (SQLException e) {
      return error(database, start, SqlErrorMapper.map(e), context);
    } catch (ApiException e) {
      return error(database, start, new QueryError(e.code, e.getMessage()), context);
    } catch (Exception e) {
      return error(
          database,
          start,
          new QueryError("EXECUTION_FAILED", "Não foi possível executar neste database."),
          context);
    }
  }

  private long elapsed(long start) {
    return (System.nanoTime() - start) / 1000000;
  }

  private DatabaseQueryResult error(String db, long start, QueryError e, ExecutionContext context) {
    return new DatabaseQueryResult(
        db, "ERROR", List.of(), List.of(), 0, elapsed(start), false, null, e, context.rowLimit);
  }
}
