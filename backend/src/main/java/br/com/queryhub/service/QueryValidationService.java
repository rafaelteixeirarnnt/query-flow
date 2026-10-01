package br.com.queryhub.service;

import br.com.queryhub.dto.ValidationResponse;
import br.com.queryhub.exception.ApiException;
import java.lang.reflect.*;
import java.util.*;
import net.sf.jsqlparser.expression.Function;
import net.sf.jsqlparser.parser.CCJSqlParserUtil;
import net.sf.jsqlparser.statement.select.PlainSelect;
import net.sf.jsqlparser.statement.select.Select;
import org.springframework.stereotype.Service;

@Service
public class QueryValidationService {
  private static final Set<String> FUNCTIONS =
      Set.of(
          "count",
          "sum",
          "avg",
          "min",
          "max",
          "lower",
          "upper",
          "length",
          "trim",
          "btrim",
          "ltrim",
          "rtrim",
          "coalesce",
          "nullif",
          "date_trunc",
          "to_char",
          "round",
          "abs",
          "substring",
          "substr",
          "replace",
          "concat",
          "concat_ws",
          "greatest",
          "least",
          "now",
          "current_database",
          "version",
          "string_agg",
          "array_agg");
  // Fail closed: every AST object, including nested CTEs and function arguments, must be known.
  private static final Set<String> NODES =
      Set.of(
          "PlainSelect",
          "ParenthesedSelect",
          "SetOperationList",
          "WithItem",
          "SelectItem",
          "Table",
          "Column",
          "Alias",
          "AliasColumn",
          "AllColumns",
          "AllTableColumns",
          "LongValue",
          "DoubleValue",
          "StringValue",
          "NullValue",
          "DateValue",
          "TimeValue",
          "TimestampValue",
          "BooleanValue",
          "HexValue",
          "Function",
          "ExpressionList",
          "ParenthesedExpressionList",
          "NamedExpressionList",
          "Addition",
          "Subtraction",
          "Multiplication",
          "Division",
          "IntegerDivision",
          "Modulo",
          "Concat",
          "EqualsTo",
          "NotEqualsTo",
          "GreaterThan",
          "GreaterThanEquals",
          "MinorThan",
          "MinorThanEquals",
          "AndExpression",
          "OrExpression",
          "XorExpression",
          "NotExpression",
          "SignedExpression",
          "Between",
          "InExpression",
          "LikeExpression",
          "IsNullExpression",
          "IsBooleanExpression",
          "ExistsExpression",
          "CaseExpression",
          "WhenClause",
          "OrderByElement",
          "GroupByElement",
          "Distinct",
          "Limit",
          "Offset",
          "Fetch",
          "Join",
          "UnionOp",
          "IntersectOp",
          "ExceptOp",
          "MinusOp");

  public ValidationResponse validate(String sql) {
    try {
      requireValid(sql);
      return new ValidationResponse(
          true,
          "SQL de leitura válido. Tabelas e permissões serão verificadas na execução.",
          "VALID");
    } catch (Exception e) {
      return new ValidationResponse(
          false,
          "SQL não permitido ou sintaxe não suportada. Use um único SELECT ou WITH de leitura e funções permitidas.",
          "SQL_NOT_ALLOWED");
    }
  }

  public void requireValid(String sql) {
    try {
      if (sql == null || sql.isBlank() || sql.length() > 100000)
        throw new IllegalArgumentException();
      var statements = CCJSqlParserUtil.parseStatements(sql, p -> p.withTimeOut(2000));
      if (statements.size() != 1 || !(statements.get(0) instanceof Select))
        throw new IllegalArgumentException();
      inspect(statements.get(0), Collections.newSetFromMap(new IdentityHashMap<>()), 0);
    } catch (Exception | StackOverflowError e) {
      throw new ApiException(400, "SQL_NOT_ALLOWED", "SQL não permitido ou sintaxe não suportada.");
    }
  }

  private void inspect(Object node, Set<Object> seen, int depth) throws IllegalAccessException {
    if (node == null
        || node instanceof String
        || node instanceof Number
        || node instanceof Boolean
        || node.getClass().isEnum()) return;
    if (depth > 128) throw new IllegalArgumentException();
    if (!seen.add(node)) return;
    if (node instanceof Iterable<?> list) {
      for (Object child : list) inspect(child, seen, depth + 1);
      return;
    }
    if (!NODES.contains(node.getClass().getSimpleName())) throw new IllegalArgumentException();
    if (node instanceof PlainSelect p && p.getIntoTables() != null && !p.getIntoTables().isEmpty())
      throw new IllegalArgumentException();
    if (node instanceof Function f && !FUNCTIONS.contains(f.getName().toLowerCase(Locale.ROOT)))
      throw new IllegalArgumentException();
    for (Class<?> type = node.getClass();
        type != null && type.getName().startsWith("net.sf.jsqlparser");
        type = type.getSuperclass()) {
      if (type.getSimpleName().equals("ASTNodeAccessImpl")) continue;
      for (Field field : type.getDeclaredFields()) {
        if (Modifier.isStatic(field.getModifiers()) || field.isSynthetic()) continue;
        field.setAccessible(true);
        Object value = field.get(node);
        if ((field.getName().equals("forMode") || field.getName().equals("forUpdateTable"))
            && value != null) throw new IllegalArgumentException();
        inspect(value, seen, depth + 1);
      }
    }
  }
}
