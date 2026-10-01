package br.com.queryhub.service;

import br.com.queryhub.config.QueryProperties;
import br.com.queryhub.dto.*;
import br.com.queryhub.exception.ApiException;
import br.com.queryhub.util.SqlErrorMapper;
import java.sql.*;
import java.util.*;
import org.springframework.stereotype.Service;

@Service
public class DatabaseDiscoveryService {
  private final ExternalConnectionFactory factory;
  private final ExternalConnectionLimiter limiter;
  private final QueryProperties props;

  public DatabaseDiscoveryService(
      ExternalConnectionFactory f, ExternalConnectionLimiter l, QueryProperties p) {
    factory = f;
    limiter = l;
    props = p;
  }

  public List<String> discover(ConnectionSnapshot snapshot) {
    try (var permit = limiter.acquire();
        var c = factory.open(snapshot, snapshot.initialDatabase());
        var s = c.createStatement()) {
      c.setReadOnly(true);
      c.setAutoCommit(false);
      s.setQueryTimeout(props.timeoutSeconds());
      var result = new ArrayList<String>();
      try (var rs =
          s.executeQuery(
              "SELECT datname FROM pg_database WHERE NOT datistemplate AND datallowconn AND has_database_privilege(datname, 'CONNECT') ORDER BY datname")) {
        while (rs.next()) {
          if (result.size() >= 10000)
            throw new ApiException(
                400,
                "TOO_MANY_DATABASES",
                "Servidor excede o limite de descoberta de 10.000 databases.");
          result.add(rs.getString(1));
        }
      }
      c.rollback();
      return result;
    } catch (SQLException e) {
      var error = SqlErrorMapper.map(e);
      throw new ApiException(503, error.code(), error.message());
    }
  }
}
