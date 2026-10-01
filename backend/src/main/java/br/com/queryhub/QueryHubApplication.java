package br.com.queryhub;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

@SpringBootApplication
@ConfigurationPropertiesScan
public class QueryHubApplication {
  public static void main(String[] args) {
    SpringApplication.run(QueryHubApplication.class, args);
  }
}
