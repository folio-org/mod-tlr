package org.folio.support;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.Getter;
import lombok.extern.log4j.Log4j2;
import java.util.UUID;

@Log4j2
@Getter
public class KafkaEvent {
  private static final ObjectMapper objectMapper = new ObjectMapper();
  public static final String STATUS = "status";
  public static final String ITEM_ID = "itemId";
  private String eventId;
  private EventType eventType;
  private JsonNode newNode;
  private JsonNode oldNode;

  public KafkaEvent(String eventPayload) {
    try {
      JsonNode jsonNode = objectMapper.readTree(eventPayload);
      setEventId(jsonNode.get("id").asText());
      setEventType(jsonNode.get("type").asText());
      setNewNode(jsonNode.get("data"));
      setOldNode(jsonNode.get("data"));
    } catch (Exception e) {
      log.error("KafkaEvent:: could not parse input payload for processing event", e);
    }
  }

  private void setEventType(String eventType) {
    this.eventType = EventType.valueOf(eventType);
  }

  private void setNewNode(JsonNode dataNode) {
    if (dataNode != null) {
      this.newNode = dataNode.get("new");
    }
  }

  private void setOldNode(JsonNode dataNode) {
    if (dataNode != null) {
      this.oldNode = dataNode.get("old");
    }
  }

  public boolean hasNewNode() {
    return newNode != null;
  }

  public static UUID getUUIDFromNode(JsonNode node, String fieldName) {
    if (node == null || !node.has(fieldName)) {
      return null;
    }
    return UUID.fromString(node.get(fieldName).asText());
  }

  public void setEventId(String eventId) {
    this.eventId = eventId;
  }

  public enum EventType {
    UPDATED, CREATED
  }
}
