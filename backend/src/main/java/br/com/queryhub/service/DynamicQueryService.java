package br.com.queryhub.service;

import br.com.queryhub.config.QueryProperties;
import br.com.queryhub.dto.*;
import br.com.queryhub.exception.ApiException;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.*;
import org.springframework.stereotype.Service;

@Service
public class DynamicQueryService {
  private final DatabaseConnectionService connections;
  private final DatabaseDiscoveryService discovery;
  private final QueryValidationService validation;
  private final DatabaseQueryRunner runner;
  private final ResultConsolidator consolidator;
  private final QueryProperties props;
  private final Semaphore active;
  private final AuditService audit;

  public DynamicQueryService(
      DatabaseConnectionService c,
      DatabaseDiscoveryService d,
      QueryValidationService v,
      DatabaseQueryRunner r,
      ResultConsolidator co,
      QueryProperties p,
      AuditService a) {
    audit = a;
    connections = c;
    discovery = d;
    validation = v;
    runner = r;
    consolidator = co;
    props = p;
    active = new Semaphore(p.maxActiveExecutions());
  }

  public ExecutionResponse execute(ExecuteQueryRequest request) {
    int rowLimit = request.rowLimit() == null ? props.maxResultRows() : request.rowLimit();
    if (rowLimit < 1 || rowLimit > props.maxResultRows())
      throw new ApiException(
          400,
          "ROW_LIMIT_INVALID",
          "Informe de 1 a " + props.maxResultRows() + " registros por database.");
    if (!active.tryAcquire())
      throw new ApiException(
          429, "EXECUTION_CAPACITY", "Há execuções em andamento. Tente novamente.");
    try {
      long start = System.nanoTime();
      var at = Instant.now();
      validation.requireValid(request.sql());
      var snapshot = connections.snapshot(request.connectionId());
      var databases = new ArrayList<>(new LinkedHashSet<>(request.databases()));
      if (databases.size() > props.maxDatabasesPerQuery())
        throw new ApiException(400, "DATABASE_LIMIT", "Quantidade máxima de databases excedida.");
      var available = discovery.discover(snapshot);
      if (!available.containsAll(databases))
        throw new ApiException(
            400, "DATABASE_NOT_ALLOWED", "Selecione somente databases disponíveis nesta conexão.");
      var context = new ExecutionContext(props.maxExecutionBytes(), rowLimit);
      List<DatabaseQueryResult> results = new ArrayList<>();
      try (var executor = Executors.newVirtualThreadPerTaskExecutor()) {
        var tasks =
            databases.stream()
                .map(db -> executor.submit(() -> runner.run(snapshot, db, request.sql(), context)))
                .toList();
        for (var task : tasks) {
          try {
            results.add(task.get());
          } catch (InterruptedException e) {
            context.cancel();
            Thread.currentThread().interrupt();
            throw new ApiException(503, "INTERRUPTED", "Execução interrompida.");
          } catch (ExecutionException e) {
            context.cancel();
            throw new ApiException(503, "EXECUTION_FAILED", "Falha ao coordenar execução.");
          }
        }
      }
      var warnings = new ArrayList<String>();
      for (var result : results) {
        if (!audit.record(context.id, snapshot, request.sql(), result)
            && !warnings.contains("AUDIT_UNAVAILABLE")) warnings.add("AUDIT_UNAVAILABLE");
      }
      int successes = (int) results.stream().filter(r -> r.status().equals("SUCCESS")).count();
      long rows = results.stream().mapToLong(DatabaseQueryResult::rowCount).sum();
      var consolidated = consolidator.consolidate(results);
      if (consolidated.truncated()) warnings.add("CONSOLIDATED_LIMIT");
      return new ExecutionResponse(
          context.id,
          snapshot.id(),
          snapshot.name(),
          at,
          (System.nanoTime() - start) / 1000000,
          new ExecutionSummary(databases.size(), successes, results.size() - successes, rows),
          results,
          consolidated,
          warnings);
    } finally {
      active.release();
    }
  }
}
