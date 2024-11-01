package org.folio.domain.dto;

import java.util.Optional;
import java.util.UUID;

import lombok.Getter;
import lombok.Setter;
import lombok.ToString;

@Getter
@ToString
public class AllowedServicePointsRequest {
  private final RequestOperation operation;
  private final String requesterId;
  @Setter
  private String instanceId;
  private final String requestId;
  private final String itemId;

  public AllowedServicePointsRequest(String operation, UUID requesterId, UUID instanceId,
    UUID requestId, UUID itemId) {

    this.operation = RequestOperation.from(operation);
    this.requesterId = asString(requesterId);
    this.instanceId = asString(instanceId);
    this.requestId = asString(requestId);
    this.itemId = asString(itemId);
  }

  private static String asString(UUID uuid) {
    return Optional.ofNullable(uuid)
      .map(UUID::toString)
      .orElse(null);
  }

  public boolean isForTitleLevelRequest() {
    return instanceId != null;
  }

}
