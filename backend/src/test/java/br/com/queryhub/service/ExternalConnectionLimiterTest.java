package br.com.queryhub.service;

import static org.assertj.core.api.Assertions.*;

import br.com.queryhub.config.QueryProperties;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;

class ExternalConnectionLimiterTest {
  @Test
  void boundsConcurrentWorkAndReleasesAfterFailure() throws Exception {
    var p = new QueryProperties(60, 10000, 5, 2, 50, 1, 10, 1048576, 33554432);
    var limiter = new ExternalConnectionLimiter(p);
    var active = new AtomicInteger();
    var peak = new AtomicInteger();
    try (var ex = Executors.newVirtualThreadPerTaskExecutor()) {
      var tasks = new java.util.ArrayList<Future<?>>();
      for (int i = 0; i < 25; i++)
        tasks.add(
            ex.submit(
                () -> {
                  try (var permit = limiter.acquire()) {
                    int n = active.incrementAndGet();
                    peak.accumulateAndGet(n, Math::max);
                    try {
                      Thread.sleep(15);
                    } catch (InterruptedException e) {
                      Thread.currentThread().interrupt();
                    } finally {
                      active.decrementAndGet();
                    }
                  }
                }));
      for (var task : tasks) task.get();
    }
    assertThat(peak.get()).isLessThanOrEqualTo(5);
    assertThat(active.get()).isZero();
    try {
      try (var permit = limiter.acquire()) {
        throw new IllegalStateException();
      }
    } catch (IllegalStateException expected) {
    }
    try (var permit = limiter.acquire()) {
      assertThat(permit).isNotNull();
    }
  }

  @Test
  void rejectsWhenPermitWaitExpires() {
    var limiter =
        new ExternalConnectionLimiter(
            new QueryProperties(60, 10000, 1, 2, 50, 1, 10, 1048576, 33554432));
    try (var first = limiter.acquire()) {
      assertThatThrownBy(limiter::acquire)
          .isInstanceOf(br.com.queryhub.exception.ApiException.class);
    }
    try (var next = limiter.acquire()) {
      assertThat(next).isNotNull();
    }
  }
}
