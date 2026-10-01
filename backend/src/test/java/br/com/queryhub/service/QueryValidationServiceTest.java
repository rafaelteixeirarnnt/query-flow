package br.com.queryhub.service;

import static org.assertj.core.api.Assertions.*;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class QueryValidationServiceTest {
  @ParameterizedTest
  @ValueSource(
      strings = {
        "SELECT 1",
        "WITH x AS (SELECT 1 AS id) SELECT * FROM x",
        "SELECT 'DELETE' AS text -- UPDATE\n",
        "SELECT id FROM t UNION ALL SELECT id FROM u",
        "SELECT count(*), lower(nome) FROM agh.exemplo GROUP BY nome",
        "SELECT CASE WHEN id=1 THEN 'a' ELSE 'b' END FROM t"
      })
  void acceptsReading(String sql) {
    assertThat(new QueryValidationService().validate(sql).valid()).isTrue();
  }

  @ParameterizedTest
  @ValueSource(
      strings = {
        "INSERT INTO t VALUES(1)",
        "UPDATE t SET id=1",
        "DELETE FROM t",
        "DROP TABLE t",
        "CREATE TABLE t(id int)",
        "SELECT 1; SELECT 2",
        "WITH x AS (DELETE FROM t RETURNING *) SELECT * FROM x",
        "SELECT * INTO t FROM u",
        "SELECT * FROM t FOR UPDATE",
        "SELECT * FROM t FOR SHARE",
        "SELECT pg_sleep(1)",
        "SELECT evil()",
        "SELECT public.lower('x')",
        "SELECT set_config('transaction_read_only','off',true)",
        "COPY t TO '/tmp/data'",
        "CALL x()",
        "DO $$BEGIN END$$",
        "nonsense"
      })
  void rejectsUnsafe(String sql) {
    assertThat(new QueryValidationService().validate(sql).valid()).as(sql).isFalse();
  }
}
