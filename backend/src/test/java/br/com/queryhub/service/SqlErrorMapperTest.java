package br.com.queryhub.service;

import static org.assertj.core.api.Assertions.*;

import br.com.queryhub.util.SqlErrorMapper;
import java.sql.SQLException;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class SqlErrorMapperTest {
  @ParameterizedTest
  @CsvSource({
    "08003,CONNECTION_LOST",
    "08006,CONNECTION_LOST",
    "08001,CONNECTION_FAILED",
    "57P01,CONNECTION_TERMINATED",
    "53300,SERVER_CONNECTION_CAPACITY",
    "57014,QUERY_TIMEOUT"
  })
  void classifiesConnectionFailuresWithoutExposingDriverDetails(String sqlState, String code) {
    var result =
        SqlErrorMapper.map(new SQLException("secret SQL credentials and hostname", sqlState));
    assertThat(result.code()).isEqualTo(code);
    assertThat(result.message()).doesNotContain("secret", "credentials", "hostname");
  }
}
