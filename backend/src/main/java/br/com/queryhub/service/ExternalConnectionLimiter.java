package br.com.queryhub.service;

import br.com.queryhub.config.QueryProperties;
import br.com.queryhub.exception.ApiException;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;
import org.springframework.stereotype.Component;

@Component
public class ExternalConnectionLimiter {
  private final Semaphore permits;
  private final int waitSeconds;

  public ExternalConnectionLimiter(QueryProperties p) {
    permits = new Semaphore(p.maxConcurrentDatabases(), true);
    waitSeconds = p.admissionTimeoutSeconds();
  }

  public Permit acquire() {
    try {
      if (!permits.tryAcquire(waitSeconds, TimeUnit.SECONDS))
        throw new ApiException(
            429, "CONNECTION_CAPACITY", "Limite de conexões em uso. Tente novamente.");
      return new Permit();
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      throw new ApiException(503, "INTERRUPTED", "Operação interrompida.");
    }
  }

  public final class Permit implements AutoCloseable {
    private final AtomicBoolean closed = new AtomicBoolean();

    public void close() {
      if (closed.compareAndSet(false, true)) permits.release();
    }
  }
}
