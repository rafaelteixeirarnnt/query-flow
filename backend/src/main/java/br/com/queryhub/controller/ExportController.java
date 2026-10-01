package br.com.queryhub.controller;

import br.com.queryhub.dto.ExportRequest;
import br.com.queryhub.service.ExportService;
import jakarta.validation.Valid;
import java.io.*;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/query/export")
public class ExportController {
  private final ExportService service;

  public ExportController(ExportService s) {
    service = s;
  }

  @PostMapping("/{format}")
  public ResponseEntity<byte[]> export(
      @PathVariable String format, @Valid @RequestBody ExportRequest r) throws IOException {
    var out = new ByteArrayOutputStream();
    String type;
    if (format.equals("csv")) {
      service.csv(r, out);
      type = "text/csv; charset=utf-8";
    } else if (format.equals("xlsx")) {
      service.xlsx(r, out);
      type = "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet";
    } else
      throw new br.com.queryhub.exception.ApiException(
          404, "FORMAT_NOT_FOUND", "Formato não encontrado.");
    return ResponseEntity.ok()
        .header("Content-Disposition", "attachment; filename=queryhub." + format)
        .header("Cache-Control", "no-store")
        .contentType(MediaType.parseMediaType(type))
        .body(out.toByteArray());
  }
}
