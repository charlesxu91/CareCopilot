package dev.carecopilot.api;

import java.lang.management.ManagementFactory;
import java.lang.management.MemoryUsage;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class PrometheusMetricsController {
  @GetMapping(value = "/actuator/prometheus", produces = MediaType.TEXT_PLAIN_VALUE)
  public String scrape() {
    MemoryUsage heap = ManagementFactory.getMemoryMXBean().getHeapMemoryUsage();
    MemoryUsage nonHeap = ManagementFactory.getMemoryMXBean().getNonHeapMemoryUsage();
    return """
        # HELP carecopilot_build_info CareCopilot build marker.
        # TYPE carecopilot_build_info gauge
        carecopilot_build_info{version="0.1.0-SNAPSHOT"} 1
        # HELP jvm_memory_used_bytes Used bytes of JVM memory.
        # TYPE jvm_memory_used_bytes gauge
        jvm_memory_used_bytes{area="heap"} %d
        jvm_memory_used_bytes{area="nonheap"} %d
        """.formatted(heap.getUsed(), nonHeap.getUsed());
  }
}
