package br.com.queryhub.config;

import jakarta.servlet.*;
import jakarta.servlet.http.*;
import java.io.*;
import java.util.concurrent.Semaphore;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

@Component
public class RequestBodyLimitFilter extends OncePerRequestFilter {
  private final Semaphore bodies = new Semaphore(4);
  private final Semaphore exports = new Semaphore(1);

  @Override
  protected void doFilterInternal(
      HttpServletRequest request, HttpServletResponse response, FilterChain chain)
      throws ServletException, IOException {
    response.setHeader("Cache-Control", "no-store");
    response.setHeader("X-Content-Type-Options", "nosniff");
    if (!request.getRequestURI().startsWith("/api/")) {
      chain.doFilter(request, response);
      return;
    }
    boolean body = request.getMethod().equals("POST") || request.getMethod().equals("PUT");
    if (!body) {
      chain.doFilter(request, response);
      return;
    }
    int max = request.getRequestURI().startsWith("/api/query/export/") ? 67108864 : 1048576;
    if (request.getContentLengthLong() > max) {
      reject(response, 413, "Requisição excede o limite de tamanho.");
      return;
    }
    Semaphore gate = request.getRequestURI().startsWith("/api/query/export/") ? exports : bodies;
    if (!gate.tryAcquire()) {
      reject(response, 429, "Capacidade de requisições excedida.");
      return;
    }
    try {
      byte[] bytes = request.getInputStream().readNBytes(max + 1);
      if (bytes.length > max) {
        reject(response, 413, "Requisição excede o limite de tamanho.");
        return;
      }
      if (bytes.length > 0
          && (request.getContentType() == null
              || !request
                  .getContentType()
                  .toLowerCase(java.util.Locale.ROOT)
                  .startsWith("application/json"))) {
        reject(response, 415, "Envie conteúdo application/json.");
        return;
      }
      chain.doFilter(
          new HttpServletRequestWrapper(request) {
            @Override
            public ServletInputStream getInputStream() {
              var source = new ByteArrayInputStream(bytes);
              return new ServletInputStream() {
                public int read() {
                  return source.read();
                }

                public boolean isFinished() {
                  return source.available() == 0;
                }

                public boolean isReady() {
                  return true;
                }

                public void setReadListener(ReadListener listener) {
                  throw new UnsupportedOperationException();
                }
              };
            }

            @Override
            public BufferedReader getReader() {
              return new BufferedReader(
                  new InputStreamReader(getInputStream(), java.nio.charset.StandardCharsets.UTF_8));
            }
          },
          response);
    } finally {
      gate.release();
    }
  }

  private void reject(HttpServletResponse response, int status, String message) throws IOException {
    response.setStatus(status);
    response.setContentType("application/problem+json");
    response.getWriter().write("{\"status\":" + status + ",\"detail\":\"" + message + "\"}");
  }
}
