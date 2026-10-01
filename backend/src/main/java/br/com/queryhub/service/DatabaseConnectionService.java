package br.com.queryhub.service;

import br.com.queryhub.domain.connection.DatabaseConnection;
import br.com.queryhub.dto.*;
import br.com.queryhub.exception.ApiException;
import br.com.queryhub.repository.DatabaseConnectionRepository;
import java.util.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional
public class DatabaseConnectionService {
  private final DatabaseConnectionRepository repo;
  private final EncryptionService crypto;

  public DatabaseConnectionService(DatabaseConnectionRepository repo, EncryptionService crypto) {
    this.repo = repo;
    this.crypto = crypto;
  }

  public ConnectionResponse create(ConnectionRequest r) {
    if (r.password() == null || r.password().isEmpty())
      throw new ApiException(400, "PASSWORD_REQUIRED", "Informe a senha da conexão.");
    var e = new DatabaseConnection();
    apply(e, r);
    return response(repo.saveAndFlush(e));
  }

  public ConnectionResponse update(long id, ConnectionRequest r) {
    var e = entity(id);
    if (r.version() == null || r.version() != e.version)
      throw new ApiException(
          409, "CONFLICT", "A conexão foi alterada. Atualize a lista e reabra o formulário.");
    apply(e, r);
    return response(repo.saveAndFlush(e));
  }

  public List<ConnectionResponse> list() {
    return repo.findAll(org.springframework.data.domain.Sort.by("name")).stream()
        .map(this::response)
        .toList();
  }

  public ConnectionResponse get(long id) {
    return response(entity(id));
  }

  public void delete(long id) {
    repo.delete(entity(id));
  }

  public ConnectionSnapshot snapshot(long id) {
    var e = entity(id);
    if (!e.active) throw new ApiException(400, "INACTIVE_CONNECTION", "A conexão está inativa.");
    return new ConnectionSnapshot(
        e.id,
        e.name,
        e.host,
        e.port,
        e.initialDatabase,
        e.username,
        e.encryptedPassword,
        e.sslEnabled);
  }

  private DatabaseConnection entity(long id) {
    return repo.findById(id)
        .orElseThrow(
            () -> new ApiException(404, "CONNECTION_NOT_FOUND", "Conexão não encontrada."));
  }

  private void apply(DatabaseConnection e, ConnectionRequest r) {
    e.name = r.name();
    e.description = r.description();
    e.host = r.host();
    e.port = r.port();
    e.initialDatabase = r.initialDatabase();
    e.username = r.username();
    e.sslEnabled = r.sslEnabled();
    e.active = r.active();
    if (r.password() != null && !r.password().isEmpty())
      e.encryptedPassword = crypto.encrypt(r.password());
  }

  private ConnectionResponse response(DatabaseConnection e) {
    return new ConnectionResponse(
        e.id,
        e.version,
        e.name,
        e.description,
        e.host,
        e.port,
        e.initialDatabase,
        e.username,
        e.sslEnabled,
        e.active,
        e.createdAt,
        e.updatedAt);
  }
}
