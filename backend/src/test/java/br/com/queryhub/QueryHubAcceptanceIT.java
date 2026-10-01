package br.com.queryhub;

import static org.assertj.core.api.Assertions.*;

import br.com.queryhub.dto.*;
import com.fasterxml.jackson.databind.JsonNode;
import java.sql.*;
import java.util.*;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.*;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Testcontainers
class QueryHubAcceptanceIT {
  @Container
  static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:17-alpine");

  @DynamicPropertySource
  static void config(DynamicPropertyRegistry r) {
    r.add("spring.datasource.url", postgres::getJdbcUrl);
    r.add("spring.datasource.username", postgres::getUsername);
    r.add("spring.datasource.password", postgres::getPassword);
    r.add("app.encryption-key", () -> Base64.getEncoder().encodeToString(new byte[32]));
    r.add("query.max-result-rows", () -> 2);
    r.add("query.timeout-seconds", () -> 1);
    r.add("query.max-concurrent-databases", () -> 2);
  }

  @Autowired TestRestTemplate http;

  @BeforeAll
  static void fixture() throws Exception {
    try (var c =
            DriverManager.getConnection(
                postgres.getJdbcUrl(), postgres.getUsername(), postgres.getPassword());
        var s = c.createStatement()) {
      s.execute(
          "CREATE ROLE reader LOGIN PASSWORD 'reader-test' NOSUPERUSER NOCREATEDB NOCREATEROLE");
      for (String db : List.of("hcac", "campo_limpo", "zaio")) s.execute("CREATE DATABASE " + db);
    }
    for (String db : List.of("hcac", "campo_limpo")) {
      try (var c =
              DriverManager.getConnection(url(db), postgres.getUsername(), postgres.getPassword());
          var s = c.createStatement()) {
        s.execute("CREATE TABLE public.exemplo(id BIGINT, nome TEXT, situacao TEXT)");
        s.execute(
            "INSERT INTO public.exemplo VALUES (9007199254740993,'João','A'),(2,'Maria',NULL),(3,'Ana','A')");
        s.execute("GRANT SELECT ON public.exemplo TO reader");
        s.execute("CREATE VIEW public.slow AS SELECT 1 AS id FROM pg_sleep(3)");
        s.execute("GRANT SELECT ON public.slow TO reader");
        s.execute("CREATE VIEW public.waiting AS SELECT 1 AS id FROM pg_sleep(0.4)");
        s.execute("GRANT SELECT ON public.waiting TO reader");
      }
    }
  }

  static String url(String db) {
    return "jdbc:postgresql://"
        + postgres.getHost()
        + ":"
        + postgres.getFirstMappedPort()
        + "/"
        + db;
  }

  Map<String, Object> form() {
    return new LinkedHashMap<>(
        Map.of(
            "name",
            "Teste",
            "host",
            postgres.getHost(),
            "port",
            postgres.getFirstMappedPort(),
            "initialDatabase",
            postgres.getDatabaseName(),
            "username",
            "reader",
            "password",
            "reader-test",
            "sslEnabled",
            false,
            "active",
            true));
  }

  long create() {
    var response = http.postForEntity("/api/connections", form(), JsonNode.class);
    assertThat(response.getStatusCode().value()).isEqualTo(201);
    assertThat(response.getBody().has("password")).isFalse();
    return response.getBody().get("id").asLong();
  }

  @Test
  void persistsEditsWithoutPasswordAndDiscovers() {
    long id = create();
    var f = form();
    f.remove("password");
    f.put("version", 0);
    f.put("name", "Editada");
    http.put("/api/connections/" + id, f);
    var test = http.postForObject("/api/connections/" + id + "/test", null, JsonNode.class);
    assertThat(test.get("success").asBoolean()).isTrue();
    var dbs = http.getForObject("/api/connections/" + id + "/databases", JsonNode.class);
    assertThat(dbs.toString()).contains("hcac", "campo_limpo", "zaio");
    http.delete("/api/connections/" + id);
    assertThat(http.getForEntity("/api/connections/" + id, JsonNode.class).getStatusCode().value())
        .isEqualTo(404);
  }

  @Test
  void executesPartiallyWithDynamicColumnsAndLimit() {
    long id = create();
    var req =
        new ExecuteQueryRequest(
            id,
            List.of("hcac", "campo_limpo", "zaio"),
            "SELECT id, nome, situacao, id AS nome, 'original' AS \"DATABASE\" FROM public.exemplo ORDER BY id");
    var response = http.postForEntity("/api/query/execute", req, ExecutionResponse.class);
    assertThat(response.getStatusCode().value()).isEqualTo(200);
    var out = response.getBody();
    assertThat(out.summary().successCount()).isEqualTo(2);
    assertThat(out.summary().errorCount()).isEqualTo(1);
    assertThat(out.summary().rowCount()).isEqualTo(4);
    for (var result : out.results().subList(0, 2)) {
      assertThat(result.rowLimit()).isEqualTo(2);
      assertThat(result.rowCount()).isEqualTo(2);
      assertThat(result.truncated()).isTrue();
      assertThat(result.truncationReason()).isEqualTo("ROW_LIMIT");
    }
    assertThat(out.consolidated().columns()).hasSize(6);
    assertThat(out.consolidated().columns().getFirst().label()).isEqualTo("DATABASE");
    assertThat(out.results().get(2).error().code()).isEqualTo("TABLE_NOT_FOUND");
  }

  @Test
  void rejectsWriteAndUndiscoveredDatabase() {
    long id = create();
    for (var request :
        List.of(
            new ExecuteQueryRequest(id, List.of("hcac"), "DELETE FROM public.exemplo"),
            new ExecuteQueryRequest(id, List.of("not_discovered"), "SELECT 1"))) {
      assertThat(
              http.postForEntity("/api/query/execute", request, JsonNode.class)
                  .getStatusCode()
                  .value())
          .isEqualTo(400);
    }
  }

  @Test
  void timesOutAndRecovers() {
    long id = create();
    var slow =
        http.postForObject(
            "/api/query/execute",
            new ExecuteQueryRequest(id, List.of("hcac"), "SELECT * FROM public.slow"),
            ExecutionResponse.class);
    assertThat(slow.results().getFirst().error().code()).isEqualTo("QUERY_TIMEOUT");
    var ok =
        http.postForObject(
            "/api/query/execute",
            new ExecuteQueryRequest(id, List.of("hcac"), "SELECT 1"),
            ExecutionResponse.class);
    assertThat(ok.summary().successCount()).isEqualTo(1);
  }

  @Test
  void emptyResultRetainsMetadataAndExactLimitNotTruncated() {
    long id = create();
    for (String where : List.of("WHERE false", "LIMIT 1", "LIMIT 2")) {
      var r =
          http.postForObject(
              "/api/query/execute",
              new ExecuteQueryRequest(id, List.of("hcac"), "SELECT * FROM public.exemplo " + where),
              ExecutionResponse.class);
      assertThat(r.results().getFirst().columns()).hasSize(3);
      assertThat(r.results().getFirst().truncated()).isFalse();
    }
  }

  @Test
  void auditsWithoutSqlAndPreservesAuditOnDelete() throws Exception {
    long id = create();
    String sql = "SELECT 42 AS value";
    var r =
        http.postForObject(
            "/api/query/execute",
            new ExecuteQueryRequest(id, List.of("hcac"), sql),
            ExecutionResponse.class);
    http.delete("/api/connections/" + id);
    try (var c =
            DriverManager.getConnection(
                postgres.getJdbcUrl(), postgres.getUsername(), postgres.getPassword());
        var st =
            c.prepareStatement(
                "SELECT connection_id, query_fingerprint, status FROM query_audit WHERE execution_id=?")) {
      st.setObject(1, r.executionId());
      try (var rs = st.executeQuery()) {
        assertThat(rs.next()).isTrue();
        assertThat(rs.getObject(1)).isNull();
        assertThat(rs.getString(2))
            .isEqualTo(
                java.util.HexFormat.of()
                    .formatHex(
                        java.security.MessageDigest.getInstance("SHA-256")
                            .digest(sql.getBytes(java.nio.charset.StandardCharsets.UTF_8))));
        assertThat(rs.getString(3)).isEqualTo("SUCCESS");
      }
    }
  }

  @Test
  void invalidCredentialsAndColumnErrorsAreSanitized() {
    var f = form();
    f.put("password", "secret-wrong-do-not-leak");
    var cr = http.postForObject("/api/connections", f, JsonNode.class);
    var test =
        http.postForObject(
            "/api/connections/" + cr.get("id").asLong() + "/test", null, JsonNode.class);
    assertThat(test.get("success").asBoolean()).isFalse();
    assertThat(test.toString()).doesNotContain("secret-wrong-do-not-leak");
    long id = create();
    var result =
        http.postForObject(
            "/api/query/execute",
            new ExecuteQueryRequest(id, List.of("hcac"), "SELECT missing FROM public.exemplo"),
            ExecutionResponse.class);
    assertThat(result.results().getFirst().error().code()).isEqualTo("COLUMN_NOT_FOUND");
  }

  @Test
  void rejectsStaleConnectionEdit() {
    long id = create();
    var first = form();
    first.put("version", 0);
    first.put("name", "First edit");
    http.put("/api/connections/" + id, first);
    var second = form();
    second.put("version", 0);
    second.put("name", "Stale edit");
    var response =
        http.exchange(
            "/api/connections/" + id,
            org.springframework.http.HttpMethod.PUT,
            new org.springframework.http.HttpEntity<>(second),
            JsonNode.class);
    assertThat(response.getStatusCode().value()).isEqualTo(409);
    assertThat(http.getForObject("/api/connections/" + id, JsonNode.class).get("name").asText())
        .isEqualTo("First edit");
  }

  @Test
  void enforcesGlobalConnectionsAcrossExecutionsTestsAndDiscovery() throws Exception {
    long id = create();
    try (var monitor =
            DriverManager.getConnection(
                postgres.getJdbcUrl(), postgres.getUsername(), postgres.getPassword());
        var executor = java.util.concurrent.Executors.newVirtualThreadPerTaskExecutor()) {
      var go = new java.util.concurrent.CountDownLatch(1);
      java.util.concurrent.Callable<ExecutionResponse> run =
          () -> {
            go.await();
            return http.postForObject(
                "/api/query/execute",
                new ExecuteQueryRequest(
                    id, List.of("hcac", "campo_limpo"), "SELECT * FROM public.waiting"),
                ExecutionResponse.class);
          };
      var a = executor.submit(run);
      var b = executor.submit(run);
      go.countDown();
      int peak = 0;
      long until = System.nanoTime() + java.util.concurrent.TimeUnit.SECONDS.toNanos(10);
      while ((!a.isDone() || !b.isDone()) && System.nanoTime() < until) {
        try (var st = monitor.createStatement();
            var rs =
                st.executeQuery(
                    "SELECT count(*) FROM pg_stat_activity WHERE application_name='QueryHub'")) {
          rs.next();
          peak = Math.max(peak, rs.getInt(1));
        }
        Thread.sleep(10);
      }
      assertThat(a.get(3, java.util.concurrent.TimeUnit.SECONDS).summary().successCount())
          .isEqualTo(2);
      assertThat(b.get(3, java.util.concurrent.TimeUnit.SECONDS).summary().successCount())
          .isEqualTo(2);
      assertThat(peak).isBetween(1, 2);
      var test =
          executor.submit(
              () -> http.postForObject("/api/connections/" + id + "/test", null, JsonNode.class));
      var discovery =
          executor.submit(
              () -> http.getForObject("/api/connections/" + id + "/databases", JsonNode.class));
      assertThat(test.get().get("success").asBoolean()).isTrue();
      assertThat(discovery.get().get("databases").size()).isGreaterThanOrEqualTo(3);
    }
  }

  @Test
  void rejectsOversizedBodyAndExportWithoutColumns() {
    var headers = new org.springframework.http.HttpHeaders();
    headers.setContentType(org.springframework.http.MediaType.APPLICATION_JSON);
    var tooLarge =
        http.postForEntity(
            "/api/query/validate",
            new org.springframework.http.HttpEntity<>(" ".repeat(1048577), headers),
            JsonNode.class);
    assertThat(tooLarge.getStatusCode().value()).isEqualTo(413);
    long id = create();
    var result =
        http.postForObject(
            "/api/query/execute",
            new ExecuteQueryRequest(id, List.of("hcac"), "SELECT 1"),
            JsonNode.class);
    ((com.fasterxml.jackson.databind.node.ObjectNode) result.get("results").get(0))
        .set("columns", new com.fasterxml.jackson.databind.ObjectMapper().createArrayNode());
    var bad =
        http.postForEntity("/api/query/export/csv", Map.of("execution", result), JsonNode.class);
    assertThat(bad.getStatusCode().value()).isEqualTo(400);
  }

  @Test
  void writesAreBlockedByPostgresReadOnlyEvenWithoutAst() throws Exception {
    try (var c =
        DriverManager.getConnection(url("hcac"), postgres.getUsername(), postgres.getPassword())) {
      c.setReadOnly(true);
      c.setAutoCommit(false);
      try (var st = c.createStatement()) {
        assertThatThrownBy(
                () -> st.executeUpdate("INSERT INTO public.exemplo VALUES(99,'blocked','X')"))
            .isInstanceOf(SQLException.class)
            .satisfies(e -> assertThat(((SQLException) e).getSQLState()).isEqualTo("25006"));
      } finally {
        c.rollback();
      }
    }
  }

  @Test
  void perExecutionLimitAppliesToEveryDatabaseAndRejectsInvalidValues() {
    long id = create();
    var config = http.getForObject("/api/query/config", JsonNode.class);
    assertThat(config.get("maxResultRows").asInt()).isEqualTo(2);
    var request =
        Map.of(
            "connectionId",
            id,
            "databases",
            List.of("hcac", "campo_limpo"),
            "sql",
            "SELECT * FROM public.exemplo",
            "rowLimit",
            1);
    var out = http.postForObject("/api/query/execute", request, ExecutionResponse.class);
    assertThat(out.results())
        .hasSize(2)
        .allSatisfy(
            r -> {
              assertThat(r.rowLimit()).isEqualTo(1);
              assertThat(r.rowCount()).isEqualTo(1);
              assertThat(r.truncated()).isTrue();
            });
    for (Object invalid : List.of(0, -1, 3, 1.5, "invalid")) {
      assertThat(
              http.postForEntity(
                      "/api/query/execute",
                      Map.of(
                          "connectionId",
                          id,
                          "databases",
                          List.of("hcac"),
                          "sql",
                          "SELECT 1",
                          "rowLimit",
                          invalid),
                      JsonNode.class)
                  .getStatusCode()
                  .value())
          .isEqualTo(400);
    }
  }
}
