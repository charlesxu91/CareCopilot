package dev.carecopilot.store;

import javax.sql.DataSource;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;

@Configuration
public class CareCopilotStateStoreConfiguration {
  @Bean
  @ConditionalOnProperty(name = "carecopilot.store.type", havingValue = "jdbc")
  DataSource careCopilotDataSource(
      @Value("${carecopilot.store.jdbc.url}") String url,
      @Value("${carecopilot.store.jdbc.username}") String username,
      @Value("${carecopilot.store.jdbc.password}") String password,
      @Value("${carecopilot.store.jdbc.driver-class-name:org.postgresql.Driver}") String driverClassName) {
    DriverManagerDataSource dataSource = new DriverManagerDataSource();
    dataSource.setDriverClassName(driverClassName);
    dataSource.setUrl(url);
    dataSource.setUsername(username);
    dataSource.setPassword(password);
    return dataSource;
  }

  @Bean
  @ConditionalOnProperty(name = "carecopilot.store.type", havingValue = "jdbc")
  JdbcTemplate careCopilotJdbcTemplate(DataSource careCopilotDataSource) {
    return new JdbcTemplate(careCopilotDataSource);
  }

  @Bean
  @Primary
  @ConditionalOnProperty(name = "carecopilot.store.type", havingValue = "jdbc")
  CareCopilotStateStore jdbcCareCopilotStateStore(JdbcTemplate careCopilotJdbcTemplate) {
    return new JdbcCareCopilotStateStore(careCopilotJdbcTemplate);
  }

  @Bean
  @ConditionalOnMissingBean(CareCopilotStateStore.class)
  CareCopilotStateStore noopCareCopilotStateStore() {
    return new NoopCareCopilotStateStore();
  }
}
