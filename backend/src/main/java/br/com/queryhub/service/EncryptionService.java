package br.com.queryhub.service;

import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.util.Base64;
import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

@Service
public class EncryptionService {
  private final SecretKeySpec key;
  private static final byte[] AAD =
      "queryhub:connection-password:v1".getBytes(StandardCharsets.UTF_8);

  public EncryptionService(@Value("${app.encryption-key}") String encoded) {
    try {
      byte[] bytes = Base64.getDecoder().decode(encoded);
      if (bytes.length != 32) throw new IllegalArgumentException();
      key = new SecretKeySpec(bytes, "AES");
    } catch (Exception e) {
      throw new IllegalStateException("APP_ENCRYPTION_KEY deve conter 32 bytes em Base64.");
    }
  }

  public String encrypt(String plain) {
    try {
      byte[] nonce = new byte[12];
      new SecureRandom().nextBytes(nonce);
      Cipher c = Cipher.getInstance("AES/GCM/NoPadding");
      c.init(Cipher.ENCRYPT_MODE, key, new GCMParameterSpec(128, nonce));
      c.updateAAD(AAD);
      return "v1:"
          + Base64.getEncoder().encodeToString(nonce)
          + ":"
          + Base64.getEncoder().encodeToString(c.doFinal(plain.getBytes(StandardCharsets.UTF_8)));
    } catch (Exception e) {
      throw new IllegalStateException("Falha ao proteger credencial.");
    }
  }

  public String decrypt(String envelope) {
    try {
      String[] p = envelope.split(":");
      if (p.length != 3 || !p[0].equals("v1")) throw new IllegalArgumentException();
      byte[] nonce = Base64.getDecoder().decode(p[1]);
      if (nonce.length != 12) throw new IllegalArgumentException();
      Cipher c = Cipher.getInstance("AES/GCM/NoPadding");
      c.init(Cipher.DECRYPT_MODE, key, new GCMParameterSpec(128, nonce));
      c.updateAAD(AAD);
      return new String(c.doFinal(Base64.getDecoder().decode(p[2])), StandardCharsets.UTF_8);
    } catch (Exception e) {
      throw new IllegalStateException("Credencial protegida inválida ou chave incompatível.");
    }
  }
}
