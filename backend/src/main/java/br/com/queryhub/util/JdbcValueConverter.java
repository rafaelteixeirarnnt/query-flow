package br.com.queryhub.util;

import br.com.queryhub.exception.ApiException;
import java.math.*;
import java.sql.*;
import java.util.*;

public final class JdbcValueConverter {
  private JdbcValueConverter() {}

  public static Object convert(Object value, int maxBytes) throws SQLException {
    if (value == null) return null;
    if (value instanceof java.sql.Array a) {
      try {
        return convertArray(a.getArray(), maxBytes);
      } finally {
        a.free();
      }
    }
    Object out;
    if (value instanceof byte[] b) {
      if (b.length > maxBytes) throw large();
      out = Base64.getEncoder().encodeToString(b);
    } else if (value instanceof BigDecimal || value instanceof BigInteger || value instanceof Long)
      out = value.toString();
    else if (value instanceof Boolean
        || value instanceof Integer
        || value instanceof Short
        || value instanceof Byte) out = value;
    else if (value instanceof Number n)
      out = Double.isFinite(n.doubleValue()) ? n : value.toString();
    else out = value.toString();
    if (out.toString().length() > maxBytes
        || out.toString().getBytes(java.nio.charset.StandardCharsets.UTF_8).length > maxBytes)
      throw large();
    return out;
  }

  private static Object convertArray(Object a, int max) throws SQLException {
    int count = java.lang.reflect.Array.getLength(a);
    if (count > 10000) throw large();
    var list = new ArrayList<Object>();
    long bytes = 2;
    for (int i = 0; i < count; i++) {
      Object v = java.lang.reflect.Array.get(a, i);
      Object converted =
          v != null && v.getClass().isArray() ? convertArray(v, max) : convert(v, max);
      bytes +=
          8
              + (converted == null
                  ? 4
                  : converted.toString().getBytes(java.nio.charset.StandardCharsets.UTF_8).length);
      if (bytes > max) throw large();
      list.add(converted);
    }
    if (list.toString().length() > max) throw large();
    return list;
  }

  private static ApiException large() {
    return new ApiException(
        400,
        "RESULT_TOO_LARGE",
        "Uma célula excedeu o limite de tamanho. Restrinja os campos da consulta.");
  }
}
