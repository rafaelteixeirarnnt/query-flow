package br.com.queryhub.service;

import static org.assertj.core.api.Assertions.*;

import br.com.queryhub.dto.*;
import java.util.*;
import org.junit.jupiter.api.Test;

class ResultConsolidatorTest {
  @Test
  void preservesDuplicateLabelsAndSourceDatabase() {
    var cols =
        List.of(
            new ColumnMetadata("c1", "id", 4, "int4", List.of("int4")),
            new ColumnMetadata("c2", "id", 12, "text", List.of("text")),
            new ColumnMetadata("c3", "DATABASE", 12, "text", List.of("text")));
    var r =
        new DatabaseQueryResult(
            "a",
            "SUCCESS",
            cols,
            List.of(Map.of("c1", 1, "c2", "second", "c3", "original")),
            1,
            0,
            false,
            null,
            null,
            2000);
    var out = new ResultConsolidator().consolidate(List.of(r));
    assertThat(out.columns()).hasSize(4);
    assertThat(out.rows().getFirst())
        .containsEntry("__database", "a")
        .containsEntry("c2", "second")
        .containsEntry("c3", "original");
  }

  @Test
  void preservesTypeDifferencesAndMissingColumns() {
    var a =
        new DatabaseQueryResult(
            "a",
            "SUCCESS",
            List.of(new ColumnMetadata("c1", "id", 4, "int4", List.of("int4"))),
            List.of(Map.of("c1", 1)),
            1,
            0,
            false,
            null,
            null,
            2000);
    var b =
        new DatabaseQueryResult(
            "b",
            "SUCCESS",
            List.of(
                new ColumnMetadata("c1", "id", 12, "text", List.of("text")),
                new ColumnMetadata("c2", "extra", 12, "text", List.of("text"))),
            List.of(Map.of("c1", "two", "c2", "value")),
            1,
            0,
            false,
            null,
            null,
            2000);
    var out = new ResultConsolidator().consolidate(List.of(a, b));
    assertThat(out.columns().get(1).sourceTypes()).containsExactly("int4", "text");
    assertThat(out.rows().getFirst()).containsEntry("c2", null);
  }

  @Test
  void boundsNullFilledConsolidationBeforeAllocatingRows() {
    var results = new ArrayList<DatabaseQueryResult>();
    for (int db = 0; db < 2; db++) {
      var columns = new ArrayList<ColumnMetadata>();
      var row = new LinkedHashMap<String, Object>();
      for (int i = 0; i < 128; i++) {
        columns.add(
            new ColumnMetadata("c" + i, "db" + db + "column" + i, 12, "text", List.of("text")));
        row.put("c" + i, "x");
      }
      results.add(
          new DatabaseQueryResult(
              "db" + db,
              "SUCCESS",
              columns,
              Collections.nCopies(1200, row),
              1200,
              1,
              false,
              null,
              null,
              2000));
    }
    var output = new ResultConsolidator().consolidate(results);
    assertThat(output.rows().size()).isLessThan(2400);
  }
}
