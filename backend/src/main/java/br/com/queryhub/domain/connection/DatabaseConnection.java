package br.com.queryhub.domain.connection;

import jakarta.persistence.*;
import java.time.Instant;

@Entity
@Table(name = "database_connection")
public class DatabaseConnection {
  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  public Long id;

  @Version public long version;

  @Column(nullable = false, length = 120)
  public String name;

  @Column(length = 1000)
  public String description;

  @Column(nullable = false, length = 253)
  public String host;

  @Column(nullable = false)
  public int port;

  @Column(nullable = false, length = 63)
  public String initialDatabase;

  @Column(nullable = false, length = 128)
  public String username;

  @Column(nullable = false, columnDefinition = "text")
  public String encryptedPassword;

  @Column(nullable = false)
  public boolean sslEnabled;

  @Column(nullable = false)
  public boolean active;

  @Column(nullable = false)
  public Instant createdAt;

  @Column(nullable = false)
  public Instant updatedAt;

  @PrePersist
  void created() {
    createdAt = updatedAt = Instant.now();
  }

  @PreUpdate
  void updated() {
    updatedAt = Instant.now();
  }
}
