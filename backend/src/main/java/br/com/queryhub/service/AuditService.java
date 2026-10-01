package br.com.queryhub.service;

import br.com.queryhub.domain.audit.QueryAudit;
import br.com.queryhub.dto.*;
import br.com.queryhub.repository.*;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

@Service
public class AuditService {
  private final QueryAuditRepository repo;
  private final DatabaseConnectionRepository connections;
  private final TransactionTemplate tx;

  public AuditService(
      QueryAuditRepository r,
      DatabaseConnectionRepository c,
      org.springframework.transaction.PlatformTransactionManager manager) {
    repo = r;
    connections = c;
    tx = new TransactionTemplate(manager);
    tx.setPropagationBehavior(
        org.springframework.transaction.TransactionDefinition.PROPAGATION_REQUIRES_NEW);
  }

  public boolean record(
      UUID id, ConnectionSnapshot snapshot, String sql, DatabaseQueryResult result) {
    try {
      String fingerprint =
          HexFormat.of()
              .formatHex(
                  MessageDigest.getInstance("SHA-256")
                      .digest(sql.getBytes(StandardCharsets.UTF_8)));
      tx.executeWithoutResult(
          status -> {
            var a = new QueryAudit();
            a.executionId = id;
            a.connectionId = connections.existsById(snapshot.id()) ? snapshot.id() : null;
            a.connectionName = snapshot.name();
            a.database = result.database();
            a.executedAt = Instant.now();
            a.status = result.status();
            a.executionTimeMs = result.executionTimeMs();
            a.rowCount = result.rowCount();
            a.queryFingerprint = fingerprint;
            a.errorMessage =
                result.error() == null
                    ? null
                    : result.error().code() + ": " + result.error().message();
            repo.saveAndFlush(a);
          });
      return true;
    } catch (Exception e) {
      org.slf4j.LoggerFactory.getLogger(getClass())
          .warn("Auditoria indisponível para execução {}", id);
      return false;
    }
  }
}
