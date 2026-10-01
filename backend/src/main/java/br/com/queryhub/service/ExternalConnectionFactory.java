package br.com.queryhub.service;

import br.com.queryhub.config.QueryProperties;
import br.com.queryhub.dto.ConnectionSnapshot;
import java.sql.*;
import org.postgresql.ds.PGSimpleDataSource;
import org.springframework.stereotype.Component;

@Component
public class ExternalConnectionFactory {
  private final EncryptionService crypto;
  private final QueryProperties props;

  public ExternalConnectionFactory(EncryptionService crypto, QueryProperties props) {
    this.crypto = crypto;
    this.props = props;
  }

  public Connection open(ConnectionSnapshot c, String database) throws SQLException {
    var ds = new PGSimpleDataSource();
    ds.setServerNames(new String[] {c.host()});
    ds.setPortNumbers(new int[] {c.port()});
    ds.setDatabaseName(database);
    ds.setUser(c.username());
    ds.setPassword(crypto.decrypt(c.encryptedPassword()));
    ds.setSslMode(c.sslEnabled() ? "verify-full" : "disable");
    ds.setConnectTimeout(props.connectTimeoutSeconds());
    ds.setSocketTimeout(props.timeoutSeconds() + props.connectTimeoutSeconds());
    ds.setApplicationName("QueryHub");
    ds.setReadOnlyMode("transaction");
    return ds.getConnection();
  }
}
