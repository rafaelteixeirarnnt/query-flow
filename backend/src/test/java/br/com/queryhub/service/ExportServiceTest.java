package br.com.queryhub.service;

import static org.assertj.core.api.Assertions.*;

import br.com.queryhub.dto.*;
import java.io.*;
import java.time.Instant;
import java.util.*;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.Test;

class ExportServiceTest {
  private ExportRequest fixture() {
    var cols = List.of(new ColumnMetadata("c1", "nome", 12, "text", List.of("text")));
    var r =
        new DatabaseQueryResult(
            "nome/muito:longo*com?caracteres[inválidos]",
            "SUCCESS",
            cols,
            List.of(Map.of("c1", "=1+1"), Map.of("c1", "João, \"A\"\nB")),
            2,
            5,
            false,
            null,
            null,
            2000);
    return new ExportRequest(
        new ExecutionResponse(
            UUID.randomUUID(),
            1,
            "Conexão",
            Instant.now(),
            5,
            new ExecutionSummary(1, 1, 0, 2),
            List.of(r),
            new ResultConsolidator().consolidate(List.of(r)),
            List.of()));
  }

  @Test
  void csvEscapesAndNeutralizesFormulas() throws Exception {
    var out = new ByteArrayOutputStream();
    new ExportService(
            new ExportValidationService(
                new br.com.queryhub.config.QueryProperties(
                    60, 2000, 5, 2, 50, 5, 10, 1048576, 33554432)),
            new ResultConsolidator())
        .csv(fixture(), out);
    assertThat(out.toString(java.nio.charset.StandardCharsets.UTF_8))
        .contains("DATABASE", "'=1+1", "\"João, \"\"A\"\"\nB\"");
  }

  @Test
  void xlsxHasSafeSheetNamesAndNoFormulas() throws Exception {
    var out = new ByteArrayOutputStream();
    new ExportService(
            new ExportValidationService(
                new br.com.queryhub.config.QueryProperties(
                    60, 2000, 5, 2, 50, 5, 10, 1048576, 33554432)),
            new ResultConsolidator())
        .xlsx(fixture(), out);
    try (var wb = new XSSFWorkbook(new ByteArrayInputStream(out.toByteArray()))) {
      assertThat(wb.getNumberOfSheets()).isEqualTo(3);
      assertThat(wb.getSheetAt(0).getSheetName()).isEqualTo("Resumo");
      assertThat(wb.getSheetAt(2).getSheetName().length()).isLessThanOrEqualTo(31);
      assertThat(wb.getSheetAt(2).getRow(1).getCell(0).getCellType().name()).isEqualTo("STRING");
    }
  }

  @Test
  void rejectsUnknownKeys() {
    var f = fixture();
    f.execution().results().getFirst().rows().getFirst();
    var bad =
        new DatabaseQueryResult(
            "x",
            "SUCCESS",
            List.of(),
            List.of(Map.of("unknown", "x")),
            1,
            0,
            false,
            null,
            null,
            2000);
    var e = f.execution();
    var r =
        new ExportRequest(
            new ExecutionResponse(
                e.executionId(),
                1,
                "x",
                Instant.now(),
                0,
                e.summary(),
                List.of(bad),
                e.consolidated(),
                List.of()));
    assertThatThrownBy(
            () ->
                new ExportValidationService(
                        new br.com.queryhub.config.QueryProperties(
                            60, 2000, 5, 2, 50, 5, 10, 1048576, 33554432))
                    .validate(r))
        .isInstanceOf(br.com.queryhub.exception.ApiException.class);
  }

  @Test
  void exportsArraysAsJsonWithoutLosingElementBoundaries() throws Exception {
    var e = fixture().execution();
    var original = e.results().getFirst();
    var result =
        new DatabaseQueryResult(
            "x",
            "SUCCESS",
            original.columns(),
            List.of(Map.of("c1", List.of("a, b", "c"))),
            1,
            0,
            false,
            null,
            null,
            2000);
    var execution =
        new ExecutionResponse(
            e.executionId(),
            1,
            "Test",
            e.executedAt(),
            0,
            e.summary(),
            List.of(result),
            new ResultConsolidator().consolidate(List.of(result)),
            List.of());
    var out = new ByteArrayOutputStream();
    new ExportService(
            new ExportValidationService(
                new br.com.queryhub.config.QueryProperties(
                    60, 2000, 5, 2, 50, 5, 10, 1048576, 33554432)),
            new ResultConsolidator())
        .xlsx(new ExportRequest(execution), out);
    try (var wb = new XSSFWorkbook(new ByteArrayInputStream(out.toByteArray()))) {
      assertThat(wb.getSheetAt(2).getRow(1).getCell(0).getStringCellValue())
          .isEqualTo("[\"a, b\",\"c\"]");
    }
  }

  @org.junit.jupiter.params.ParameterizedTest
  @org.junit.jupiter.params.provider.ValueSource(ints = {500, 10001})
  void exportUsesConfiguredRowLimit(int limit) {
    var original = fixture().execution();
    var validator =
        new ExportValidationService(
            new br.com.queryhub.config.QueryProperties(
                60, limit, 5, 2, 50, 5, 10, 1048576, 33554432));
    for (int count : new int[] {limit, limit + 1}) {
      var result =
          new DatabaseQueryResult(
              "qa",
              "SUCCESS",
              original.results().getFirst().columns(),
              Collections.nCopies(count, Map.<String, Object>of("c1", "test")),
              count,
              0,
              true,
              "ROW_LIMIT",
              null,
              limit);
      var request =
          new ExportRequest(
              new ExecutionResponse(
                  original.executionId(),
                  1,
                  "QA",
                  original.executedAt(),
                  0,
                  new ExecutionSummary(1, 1, 0, count),
                  List.of(result),
                  original.consolidated(),
                  List.of()));
      if (count == limit)
        assertThatCode(() -> validator.validate(request)).doesNotThrowAnyException();
      else
        assertThatThrownBy(() -> validator.validate(request))
            .isInstanceOf(br.com.queryhub.exception.ApiException.class);
    }
  }
}
