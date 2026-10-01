package br.com.queryhub.config;

import static org.assertj.core.api.Assertions.*;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.context.ConfigDataApplicationContextInitializer;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Configuration;

class QueryPropertiesTest {
  private final ApplicationContextRunner runner =
      new ApplicationContextRunner()
          .withInitializer(new ConfigDataApplicationContextInitializer())
          .withUserConfiguration(Config.class);

  @Configuration(proxyBeanMethods = false)
  @EnableConfigurationProperties(QueryProperties.class)
  static class Config {}

  @Test
  void defaultsToOneThousand() {
    runner.run(c -> assertThat(c.getBean(QueryProperties.class).maxResultRows()).isEqualTo(1000));
  }

  @ParameterizedTest
  @ValueSource(strings = {"500", "5000", "10001"})
  void acceptsEnvironmentOverride(String value) {
    runner
        .withPropertyValues("CONSULTA_LIMITE_REGISTROS=" + value)
        .run(
            c ->
                assertThat(c.getBean(QueryProperties.class).maxResultRows())
                    .isEqualTo(Integer.parseInt(value)));
  }

  @ParameterizedTest
  @ValueSource(strings = {"0", "-1", "invalid", "1.5"})
  void rejectsInvalidConfigurationAtStartup(String value) {
    runner
        .withPropertyValues("CONSULTA_LIMITE_REGISTROS=" + value)
        .run(c -> assertThat(c).hasFailed());
  }
}
