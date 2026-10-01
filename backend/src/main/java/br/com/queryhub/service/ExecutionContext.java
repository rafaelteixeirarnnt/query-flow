package br.com.queryhub.service;

import java.sql.Statement;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public class ExecutionContext {
  public final UUID id = UUID.randomUUID();
  public final ExecutionBudget budget;
  public final int rowLimit;
  private final Set<Statement> statements = ConcurrentHashMap.newKeySet();

  public ExecutionContext(long bytes, int rowLimit) {
    this.rowLimit = rowLimit;
    budget = new ExecutionBudget(bytes);
  }

  public void register(Statement s) {
    statements.add(s);
  }

  public void unregister(Statement s) {
    statements.remove(s);
  }

  public void cancel() {
    for (var s : statements) {
      try {
        s.cancel();
      } catch (java.sql.SQLException ignored) {
      }
    }
  }
}
