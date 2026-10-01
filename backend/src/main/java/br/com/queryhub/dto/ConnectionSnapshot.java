package br.com.queryhub.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreType;

@JsonIgnoreType
public record ConnectionSnapshot(
    long id,
    String name,
    String host,
    int port,
    String initialDatabase,
    String username,
    String encryptedPassword,
    boolean sslEnabled) {
  @Override
  public String toString() {
    return "ConnectionSnapshot[id=" + id + "]";
  }
}
