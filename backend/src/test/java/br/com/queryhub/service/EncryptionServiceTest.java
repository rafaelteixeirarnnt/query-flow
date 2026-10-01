package br.com.queryhub.service;

import static org.assertj.core.api.Assertions.*;

import java.util.Base64;
import org.junit.jupiter.api.Test;

class EncryptionServiceTest {
  private final String key = Base64.getEncoder().encodeToString(new byte[32]);

  @Test
  void roundTripAndUniqueNonce() {
    var s = new EncryptionService(key);
    var a = s.encrypt("segredo çã 🔑");
    assertThat(a).isNotEqualTo(s.encrypt("segredo çã 🔑"));
    assertThat(s.decrypt(a)).isEqualTo("segredo çã 🔑");
  }

  @Test
  void rejectsTampering() {
    var s = new EncryptionService(key);
    var parts = s.encrypt("secret").split(":");
    byte[] bytes = Base64.getDecoder().decode(parts[2]);
    bytes[0] ^= 1;
    assertThatThrownBy(
            () ->
                s.decrypt(
                    parts[0] + ":" + parts[1] + ":" + Base64.getEncoder().encodeToString(bytes)))
        .isInstanceOf(IllegalStateException.class);
  }

  @Test
  void rejectsWrongKeyAndLength() {
    var s = new EncryptionService(key);
    byte[] other = new byte[32];
    other[0] = 1;
    var t = new EncryptionService(Base64.getEncoder().encodeToString(other));
    assertThatThrownBy(() -> t.decrypt(s.encrypt("secret")))
        .isInstanceOf(IllegalStateException.class);
    assertThatThrownBy(() -> new EncryptionService("invalid"))
        .isInstanceOf(IllegalStateException.class);
  }
}
