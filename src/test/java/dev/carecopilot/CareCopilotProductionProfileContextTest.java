package dev.carecopilot;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest(properties = {
    "spring.profiles.active=prod",
    "carecopilot.store.jdbc.url=jdbc:h2:mem:carecopilot-prod-context;MODE=PostgreSQL;DB_CLOSE_DELAY=-1",
    "carecopilot.store.jdbc.username=sa",
    "carecopilot.store.jdbc.password=",
    "carecopilot.store.jdbc.driver-class-name=org.h2.Driver",
    "carecopilot.agentplane.base-url=http://127.0.0.1:18080"
})
class CareCopilotProductionProfileContextTest {
  @Test
  void startsWithJdbcStateStore() {
  }
}
