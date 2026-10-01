package br.com.queryhub.exception;

import org.springframework.http.*;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.*;

@RestControllerAdvice
public class ApiExceptionHandler {
  @ExceptionHandler(ApiException.class)
  ResponseEntity<ProblemDetail> api(ApiException e) {
    return problem(e.status, e.code, e.getMessage());
  }

  @ExceptionHandler({
    MethodArgumentNotValidException.class,
    HttpMessageNotReadableException.class,
    IllegalArgumentException.class
  })
  ResponseEntity<ProblemDetail> invalid(Exception e) {
    return problem(
        400, "INVALID_REQUEST", "Revise os campos informados e os limites da requisição.");
  }

  @ExceptionHandler(ObjectOptimisticLockingFailureException.class)
  ResponseEntity<ProblemDetail> conflict(Exception e) {
    return problem(409, "CONFLICT", "A conexão foi alterada. Atualize a página e tente novamente.");
  }

  @ExceptionHandler(Exception.class)
  ResponseEntity<ProblemDetail> unexpected(Exception e) {
    org.slf4j.LoggerFactory.getLogger(getClass())
        .error("Falha interna: {}", e.getClass().getSimpleName());
    return problem(
        503,
        "SERVICE_UNAVAILABLE",
        "Operação indisponível. Verifique a configuração e tente novamente.");
  }

  private ResponseEntity<ProblemDetail> problem(int status, String code, String message) {
    var p = ProblemDetail.forStatusAndDetail(HttpStatusCode.valueOf(status), message);
    p.setProperty("code", code);
    return ResponseEntity.status(status).body(p);
  }
}
