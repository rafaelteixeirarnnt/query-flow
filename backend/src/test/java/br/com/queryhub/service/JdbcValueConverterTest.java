package br.com.queryhub.service;

import static org.assertj.core.api.Assertions.*;

import br.com.queryhub.util.JdbcValueConverter;
import org.junit.jupiter.api.Test;

class JdbcValueConverterTest {
  @Test
  void rejectsUtf8ArrayLargerThanCellBudget() throws Exception {
    String[] values = new String[10000];
    java.util.Arrays.fill(values, "漢".repeat(80));
    java.sql.Array array =
        new java.sql.Array() {
          public String getBaseTypeName() {
            return "text";
          }

          public int getBaseType() {
            return java.sql.Types.VARCHAR;
          }

          public Object getArray() {
            return values;
          }

          public Object getArray(java.util.Map<String, Class<?>> m) {
            return values;
          }

          public Object getArray(long i, int n) {
            throw new UnsupportedOperationException();
          }

          public Object getArray(long i, int n, java.util.Map<String, Class<?>> m) {
            throw new UnsupportedOperationException();
          }

          public java.sql.ResultSet getResultSet() {
            throw new UnsupportedOperationException();
          }

          public java.sql.ResultSet getResultSet(java.util.Map<String, Class<?>> m) {
            throw new UnsupportedOperationException();
          }

          public java.sql.ResultSet getResultSet(long i, int n) {
            throw new UnsupportedOperationException();
          }

          public java.sql.ResultSet getResultSet(long i, int n, java.util.Map<String, Class<?>> m) {
            throw new UnsupportedOperationException();
          }

          public void free() {}
        };
    assertThatThrownBy(() -> JdbcValueConverter.convert(array, 1048576))
        .isInstanceOf(br.com.queryhub.exception.ApiException.class);
  }
}
