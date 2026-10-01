package br.com.queryhub.service;

import br.com.queryhub.dto.*;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.ss.util.WorkbookUtil;
import org.apache.poi.xssf.streaming.SXSSFWorkbook;
import org.springframework.stereotype.Service;

@Service
public class ExportService {
  private final ExportValidationService validation;
  private final ResultConsolidator consolidator;

  public ExportService(ExportValidationService v, ResultConsolidator c) {
    validation = v;
    consolidator = c;
  }

  public void csv(ExportRequest r, OutputStream out) throws IOException {
    validation.validate(r);
    var table = consolidator.consolidate(r.execution().results());
    var writer = new OutputStreamWriter(out, StandardCharsets.UTF_8);
    writer.write('\ufeff');
    line(writer, table.columns().stream().map(ColumnMetadata::label).toList());
    for (var row : table.rows())
      line(writer, table.columns().stream().map(c -> text(row.get(c.key()))).toList());
    writer.flush();
  }

  private String text(Object v) {
    if (v instanceof List<?>) {
      try {
        return new com.fasterxml.jackson.databind.ObjectMapper().writeValueAsString(v);
      } catch (com.fasterxml.jackson.core.JsonProcessingException e) {
        throw new br.com.queryhub.exception.ApiException(
            400, "INVALID_EXPORT", "Valor estruturado inválido.");
      }
    }
    return v == null ? "" : v.toString();
  }

  private void line(Writer w, List<String> values) throws IOException {
    for (int i = 0; i < values.size(); i++) {
      if (i > 0) w.write(',');
      String value = values.get(i);
      String trimmed = value.stripLeading();
      if (!trimmed.isEmpty() && "=+-@".indexOf(trimmed.charAt(0)) >= 0
          || value.startsWith("\t")
          || value.startsWith("\r")) value = "'" + value;
      w.write('"');
      w.write(value.replace("\"", "\"\""));
      w.write('"');
    }
    w.write("\r\n");
  }

  public void xlsx(ExportRequest r, OutputStream out) throws IOException {
    validation.validate(r);
    var e = r.execution();
    try (var wb = new SXSSFWorkbook(100)) {
      try {
        var style = wb.createCellStyle();
        var font = wb.createFont();
        font.setBold(true);
        style.setFont(font);
        style.setFillForegroundColor(IndexedColors.GREY_25_PERCENT.getIndex());
        style.setFillPattern(FillPatternType.SOLID_FOREGROUND);
        var summary = wb.createSheet("Resumo");
        int i = 0;
        long successes = e.results().stream().filter(x -> x.status().equals("SUCCESS")).count();
        for (var entry :
            List.of(
                List.of("Conexão", e.connectionName()),
                List.of("Data/Hora", e.executedAt().toString()),
                List.of("Databases", e.results().size() + ""),
                List.of("Sucessos", successes + ""),
                List.of("Erros", (e.results().size() - successes) + ""),
                List.of(
                    "Registros",
                    e.results().stream().mapToLong(DatabaseQueryResult::rowCount).sum() + ""),
                List.of("Tempo (ms)", e.executionTimeMs() + ""))) {
          var row = summary.createRow(i++);
          row.createCell(0).setCellValue(entry.get(0));
          row.createCell(1).setCellValue(entry.get(1));
        }
        i++;
        for (var result : e.results()) {
          var row = summary.createRow(i++);
          row.createCell(0).setCellValue(result.database());
          row.createCell(1).setCellValue(result.status());
          row.createCell(2)
              .setCellValue(
                  result.error() != null
                      ? result.error().message()
                      : result.truncated()
                          ? "Resultado limitado: " + result.truncationReason()
                          : "");
        }
        summary.setColumnWidth(0, 8000);
        summary.setColumnWidth(1, 8000);
        summary.setColumnWidth(2, 14000);
        table(wb.createSheet("Consolidado"), consolidator.consolidate(e.results()), style);
        Set<String> names = new HashSet<>(Set.of("resumo", "consolidado"));
        for (var result : e.results()) {
          String base = WorkbookUtil.createSafeSheetName(result.database());
          if (base.isBlank()) base = "Database";
          String name = base;
          int suffix = 1;
          while (!names.add(name.toLowerCase(Locale.ROOT))) {
            String s = "_" + suffix++;
            name = base.substring(0, Math.min(base.length(), 31 - s.length())) + s;
          }
          table(wb.createSheet(name), new TabularResult(result.columns(), result.rows()), style);
        }
        wb.write(out);
      } finally {
        wb.dispose();
      }
    }
  }

  private void table(Sheet sheet, TabularResult data, CellStyle header) throws IOException {
    var h = sheet.createRow(0);
    for (int i = 0; i < data.columns().size(); i++) {
      var c = h.createCell(i);
      c.setCellValue(data.columns().get(i).label());
      c.setCellStyle(header);
      sheet.setColumnWidth(i, 6500);
    }
    int ri = 1;
    for (var values : data.rows()) {
      var row = sheet.createRow(ri++);
      for (int i = 0; i < data.columns().size(); i++) {
        String value = text(values.get(data.columns().get(i).key()));
        if (value.length() > 32767)
          throw new br.com.queryhub.exception.ApiException(
              400,
              "XLSX_CELL_LIMIT",
              "Uma célula excede 32.767 caracteres. Use CSV ou reduza a consulta.");
        row.createCell(i).setCellValue(value);
      }
    }
    sheet.createFreezePane(0, 1);
  }
}
