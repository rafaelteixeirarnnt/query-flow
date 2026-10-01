package br.com.queryhub.controller;

import br.com.queryhub.dto.*;
import br.com.queryhub.service.*;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/query")
public class QueryController {
  private final br.com.queryhub.config.QueryProperties props;
  private final QueryValidationService validation;
  private final DynamicQueryService execution;

  public QueryController(
      QueryValidationService v, DynamicQueryService e, br.com.queryhub.config.QueryProperties p) {
    props = p;
    validation = v;
    execution = e;
  }

  @GetMapping("/config")
  public java.util.Map<String, Integer> config() {
    return java.util.Map.of("maxResultRows", props.maxResultRows());
  }

  @PostMapping("/validate")
  public ValidationResponse validate(@Valid @RequestBody ValidationRequest r) {
    return validation.validate(r.sql());
  }

  @PostMapping("/execute")
  public ExecutionResponse execute(@Valid @RequestBody ExecuteQueryRequest r) {
    return execution.execute(r);
  }
}
