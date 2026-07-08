package dev.carecopilot.store;

import static org.assertj.core.api.Assertions.assertThat;

import dev.carecopilot.domain.CareAuditEventView;
import dev.carecopilot.domain.CaseView;
import dev.carecopilot.domain.TimelineEntryView;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;

class CareCopilotStateStoreTest {
  @Test
  void persistsAndLoadsCareCopilotState() {
    JdbcCareCopilotStateStore store = new JdbcCareCopilotStateStore(jdbcTemplate());
    CareCopilotState state = new CareCopilotState(
        Map.of("case-1", new CaseView(
            "case-1",
            "agent-session-1",
            "测试用户",
            36,
            "未知",
            List.of("青霉素"),
            List.of("二甲双胍"),
            Instant.parse("2026-07-08T00:00:00Z"))),
        Map.of("case-1", List.of(new TimelineEntryView(
            "timeline-1",
            "case-1",
            "CASE_CREATED",
            "创建健康档案",
            "已创建会话。",
            null,
            null,
            Instant.parse("2026-07-08T00:00:01Z")))),
        List.of(new CareAuditEventView(
            "audit-1",
            "case-1",
            "CASE_CREATED",
            "carecopilot-api",
            "创建健康档案",
            null,
            null,
            "TRAINABLE_TELEMETRY",
            Instant.parse("2026-07-08T00:00:01Z"))));

    store.save(state);

    CareCopilotState loaded = store.load();
    assertThat(loaded.cases()).containsKey("case-1");
    assertThat(loaded.timelines().get("case-1")).extracting(TimelineEntryView::eventType).containsExactly("CASE_CREATED");
    assertThat(loaded.auditEvents()).extracting(CareAuditEventView::eventType).containsExactly("CASE_CREATED");
  }

  private static JdbcTemplate jdbcTemplate() {
    DriverManagerDataSource dataSource = new DriverManagerDataSource();
    dataSource.setDriverClassName("org.h2.Driver");
    dataSource.setUrl("jdbc:h2:mem:carecopilot-" + java.util.UUID.randomUUID() + ";MODE=PostgreSQL;DB_CLOSE_DELAY=-1");
    dataSource.setUsername("sa");
    dataSource.setPassword("");
    return new JdbcTemplate(dataSource);
  }
}
