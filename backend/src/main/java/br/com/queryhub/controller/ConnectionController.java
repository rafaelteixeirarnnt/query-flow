package br.com.queryhub.controller;

import br.com.queryhub.dto.*;
import br.com.queryhub.service.*;
import jakarta.validation.Valid;
import java.util.*;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/connections")
public class ConnectionController {
  private final DatabaseConnectionService service;
  private final ConnectionTestService tests;
  private final DatabaseDiscoveryService discovery;

  public ConnectionController(
      DatabaseConnectionService s, ConnectionTestService t, DatabaseDiscoveryService d) {
    service = s;
    tests = t;
    discovery = d;
  }

  @GetMapping
  public List<ConnectionResponse> list() {
    return service.list();
  }

  @GetMapping("/{id}")
  public ConnectionResponse get(@PathVariable long id) {
    return service.get(id);
  }

  @PostMapping
  public ResponseEntity<ConnectionResponse> create(@Valid @RequestBody ConnectionRequest r) {
    return ResponseEntity.status(201).body(service.create(r));
  }

  @PutMapping("/{id}")
  public ConnectionResponse update(@PathVariable long id, @Valid @RequestBody ConnectionRequest r) {
    return service.update(id, r);
  }

  @DeleteMapping("/{id}")
  @ResponseStatus(HttpStatus.NO_CONTENT)
  public void delete(@PathVariable long id) {
    service.delete(id);
  }

  @PostMapping("/{id}/test")
  public ConnectionTestResponse test(@PathVariable long id) {
    return tests.test(id);
  }

  @GetMapping("/{id}/databases")
  public DatabaseListResponse databases(@PathVariable long id) {
    return new DatabaseListResponse(discovery.discover(service.snapshot(id)));
  }
}
