package br.com.queryhub.service;

public class ExecutionBudget {
  private long remaining;

  public ExecutionBudget(long bytes) {
    remaining = bytes;
  }

  public synchronized boolean reserve(long bytes) {
    if (bytes > remaining) return false;
    remaining -= bytes;
    return true;
  }
}
