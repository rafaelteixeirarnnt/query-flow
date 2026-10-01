package br.com.queryhub.domain.audit;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "query_audit")
public class QueryAudit {
  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  public Long id;

  @Column(nullable = false)
  public UUID executionId;

  public Long connectionId;

  @Column(nullable = false, length = 120)
  public String connectionName;

  @Column(nullable = false, length = 63)
  public String database;

  @Column(nullable = false)
  public Instant executedAt;

  @Column(nullable = false, length = 16)
  public String status;

  @Column(nullable = false)
  public long executionTimeMs;

  @Column(nullable = false)
  public int rowCount;

  @Column(nullable = false, length = 64)
  public String queryFingerprint;

  @Column(length = 1000)
  public String errorMessage;
}
