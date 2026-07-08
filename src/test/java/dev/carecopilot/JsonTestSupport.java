package dev.carecopilot;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

final class JsonTestSupport {
  private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

  private JsonTestSupport() {
  }

  static String extractString(String json, String fieldName) throws Exception {
    JsonNode root = OBJECT_MAPPER.readTree(json);
    return root.get(fieldName).asText();
  }
}
