package br.com.queryhub.service;

import br.com.queryhub.dto.*;
import br.com.queryhub.util.SqlErrorMapper;
import java.sql.*;
import org.springframework.stereotype.Service;

@Service
public class ConnectionTestService {
  private final DatabaseConnectionService connections;
  private final ExternalConnectionFactory factory;
  private final ExternalConnectionLimiter limiter;

  public ConnectionTestService(
      DatabaseConnectionService c, ExternalConnectionFactory f, ExternalConnectionLimiter l) {
    connections = c;
    factory = f;
    limiter = l;
  }

  public ConnectionTestResponse test(long id) {
    var snapshot = connections.snapshot(id);
    long start = System.nanoTime();
    try (var permit = limiter.acquire();
        var c = factory.open(snapshot, snapshot.initialDatabase())) {
      boolean ok = c.isValid(5);
      return new ConnectionTestResponse(
          ok,
          ok ? "Conexão realizada com sucesso." : "Servidor não respondeu à validação.",
          (System.nanoTime() - start) / 1000000);
    } catch (SQLException e) {
      return new ConnectionTestResponse(
          false, SqlErrorMapper.map(e).message(), (System.nanoTime() - start) / 1000000);
    }
  }
}
