package dev.carecopilot.store;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import java.sql.Timestamp;
import java.time.Instant;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;

public class JdbcCareCopilotStateStore implements CareCopilotStateStore {
  private static final String STATE_KEY = "carecopilot";

  private final JdbcTemplate jdbcTemplate;
  private final ObjectMapper objectMapper = new ObjectMapper().registerModule(new JavaTimeModule());

  public JdbcCareCopilotStateStore(JdbcTemplate jdbcTemplate) {
    this.jdbcTemplate = jdbcTemplate;
    createTable();
  }

  @Override
  public synchronized CareCopilotState load() {
    try {
      String json = jdbcTemplate.queryForObject(
          "select state_json from carecopilot_state where state_key = ?",
          String.class,
          STATE_KEY);
      return objectMapper.readValue(json, CareCopilotState.class);
    } catch (EmptyResultDataAccessException exception) {
      return CareCopilotState.empty();
    } catch (JsonProcessingException exception) {
      throw new IllegalStateException("Failed to decode CareCopilot JDBC state.", exception);
    }
  }

  @Override
  public synchronized void save(CareCopilotState state) {
    try {
      jdbcTemplate.update("delete from carecopilot_state where state_key = ?", STATE_KEY);
      jdbcTemplate.update(
          "insert into carecopilot_state (state_key, state_json, updated_at) values (?, ?, ?)",
          STATE_KEY,
          objectMapper.writeValueAsString(state),
          Timestamp.from(Instant.now()));
    } catch (JsonProcessingException exception) {
      throw new IllegalStateException("Failed to encode CareCopilot JDBC state.", exception);
    }
  }

  private void createTable() {
    jdbcTemplate.execute("""
        create table if not exists carecopilot_state (
          state_key varchar(128) primary key,
          state_json text not null,
          updated_at timestamp not null
        )
        """);
  }
}
