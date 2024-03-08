package org.folio.support;

import lombok.SneakyThrows;
import org.folio.EcsTlrApplication;
import org.folio.domain.entity.EcsTlrEntity;
import org.folio.spring.integration.XOkapiHeaders;
import org.springframework.messaging.MessageHeaders;
import org.testcontainers.shaded.org.apache.commons.io.IOUtils;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.Map;
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

  public static MessageHeaders getMessageHeaders() {
    Map<String, Object> header = new HashMap<>();
    header.put(XOkapiHeaders.TENANT, "test-tenant".getBytes());
    return new MessageHeaders(header);
  }
}
