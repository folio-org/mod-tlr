package org.folio.support;

import lombok.SneakyThrows;
import org.folio.EcsTlrApplication;
import org.folio.domain.entity.EcsTlrEntity;
import org.folio.domain.dto.Loan;
import org.folio.domain.dto.Metadata;
import org.folio.domain.dto.Request;
import org.folio.domain.dto.Request.EcsRequestPhaseEnum;
import org.folio.domain.dto.Request.StatusEnum;
import org.folio.domain.dto.CirculationClaimItemReturnedRequest;
import org.folio.domain.dto.ClaimItemReturnedRequest;
import org.folio.domain.dto.CirculationDeclareItemLostRequest;
import org.folio.domain.dto.DeclareItemLostRequest;
import org.testcontainers.shaded.org.apache.commons.io.IOUtils;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.Date;
import java.util.UUID;
import java.util.stream.Stream;

public class MockDataUtils {

  public static final UUID PRIMARY_REQUEST_ID = UUID.fromString("398501a2-5c97-4ba6-9ee7-d1cd64339999");
  public static final UUID SECONDARY_REQUEST_ID = UUID.fromString("398501a2-5c97-4ba6-9ee7-d1cd6433cb98");
  public static final UUID ITEM_ID = UUID.fromString("100d10bf-2f06-4aa0-be15-0b95b2d9f9e3");

  public static EcsTlrEntity getEcsTlrEntity() {
    return EcsTlrEntity.builder()
      .id(UUID.randomUUID())
      .primaryRequestId(PRIMARY_REQUEST_ID)
      .secondaryRequestId(SECONDARY_REQUEST_ID)
      .build();
  }

  public static EcsTlrEntity buildEcsTlrEntity(UUID itemId, UUID requesterId,
    UUID primaryRequestId, UUID secondaryRequestId, String primaryRequestTenantId,
    String secondaryRequestTenantId) {

    EcsTlrEntity ecsTlr = new EcsTlrEntity();
    ecsTlr.setItemId(itemId);
    ecsTlr.setRequesterId(requesterId);
    ecsTlr.setPrimaryRequestId(primaryRequestId);
    ecsTlr.setSecondaryRequestId(secondaryRequestId);
    ecsTlr.setPrimaryRequestTenantId(primaryRequestTenantId);
    ecsTlr.setSecondaryRequestTenantId(secondaryRequestTenantId);

    return ecsTlr;
  }

  public static Loan buildLoan(UUID loanId, UUID userId, UUID itemId, Date createdDate) {
    return new Loan()
      .id(loanId.toString())
      .userId(userId.toString())
      .itemId(itemId.toString())
      .metadata(new Metadata().createdDate(createdDate));
  }

  public static Request buildEcsRequest(UUID requestId, UUID userId, UUID itemId, Date updatedDate,
    EcsRequestPhaseEnum ecsRequestPhase, StatusEnum status) {

    return new Request()
      .id(requestId.toString())
      .ecsRequestPhase(ecsRequestPhase)
      .requesterId(userId.toString())
      .itemId(itemId.toString())
      .status(status)
      .metadata(new Metadata().updatedDate(updatedDate));
  }

  public static CirculationClaimItemReturnedRequest buildCirculationClaimItemReturnedRequest(
    Date claimItemReturnedDate, String comment) {

    return new CirculationClaimItemReturnedRequest()
      .itemClaimedReturnedDateTime(claimItemReturnedDate)
      .comment(comment);
  }

  public static ClaimItemReturnedRequest buildClaimItemReturnedRequest(UUID loanId,
    Date claimItemReturnedDate, String comment) {

    return new ClaimItemReturnedRequest()
      .loanId(loanId)
      .itemClaimedReturnedDateTime(claimItemReturnedDate)
      .comment(comment);
  }

  public static ClaimItemReturnedRequest buildClaimItemReturnedRequest(UUID userId, UUID itemId,
    Date claimItemReturnedDate, String comment) {

    return new ClaimItemReturnedRequest()
      .userId(userId)
      .itemId(itemId)
      .itemClaimedReturnedDateTime(claimItemReturnedDate)
      .comment(comment);
  }

  public static CirculationDeclareItemLostRequest buildCirculationDeclareItemLostRequest(
    UUID servicePointId, Date declaredLostDate, String comment) {

    return new CirculationDeclareItemLostRequest()
      .servicePointId(servicePointId)
      .declaredLostDateTime(declaredLostDate)
      .comment(comment);
  }

  public static DeclareItemLostRequest buildDeclareItemLostRequest(UUID loanId,
    UUID servicePointId, Date declaredLostDate, String comment) {

    return new DeclareItemLostRequest()
      .loanId(loanId)
      .servicePointId(servicePointId)
      .declaredLostDateTime(declaredLostDate)
      .comment(comment);
  }

  public static DeclareItemLostRequest buildDeclareItemLostRequest(UUID userId, UUID itemId,
    UUID servicePointId, Date declaredLostDate, String comment) {

    return new DeclareItemLostRequest()
      .userId(userId)
      .itemId(itemId)
      .servicePointId(servicePointId)
      .declaredLostDateTime(declaredLostDate)
      .comment(comment);
  }

  @SneakyThrows
  public static String getMockDataAsString(String path) {
    try (InputStream resourceAsStream = EcsTlrApplication.class.getClassLoader().getResourceAsStream(path)) {
      if (resourceAsStream != null) {
        return IOUtils.toString(resourceAsStream, StandardCharsets.UTF_8);
      } else {
        StringBuilder sb = new StringBuilder();
        try (Stream<String> lines = Files.lines(Paths.get(path))) {
          lines.forEach(sb::append);
        }
        return sb.toString();
      }
    }
  }
}
