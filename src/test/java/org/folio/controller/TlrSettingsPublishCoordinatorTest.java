package org.folio.controller;

import static com.github.tomakehurst.wiremock.client.WireMock.equalToJson;
import static com.github.tomakehurst.wiremock.client.WireMock.okJson;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static com.github.tomakehurst.wiremock.client.WireMock.postRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlMatching;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.UUID;

import org.folio.api.BaseIT;
import org.folio.domain.dto.TlrSettings;
import org.folio.domain.entity.TlrSettingsEntity;
import org.folio.domain.mapper.TlrSettingsMapper;
import org.folio.domain.mapper.TlrSettingsMapperImpl;
import org.folio.repository.TlrSettingsRepository;
import org.folio.service.PublishCoordinatorService;
import org.folio.service.impl.TlrSettingsServiceImpl;
import org.folio.spring.service.SystemUserScopedExecutionService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;

import lombok.SneakyThrows;

@ExtendWith(MockitoExtension.class)
public class TlrSettingsPublishCoordinatorTest extends BaseIT {
  public static final String PUBLICATIONS_URL_PATTERN = "/consortia/%s/publications";
  @Mock
  private TlrSettingsRepository tlrSettingsRepository;
  @Spy
  private TlrSettingsMapper tlrSettingsMapper = new TlrSettingsMapperImpl();
  @Autowired
  private PublishCoordinatorService<TlrSettings> publishCoordinatorService;
  @Mock
  private SystemUserScopedExecutionService systemUserScopedExecutionService;
  private TlrSettingsServiceImpl tlrSettingsService;
  private TlrSettingsController tlrSettingsController;

  @BeforeEach
  void before() {
    tlrSettingsService = new TlrSettingsServiceImpl(tlrSettingsRepository, tlrSettingsMapper,
      publishCoordinatorService, systemUserScopedExecutionService);
    tlrSettingsController = new TlrSettingsController(tlrSettingsService);
  }

  @SneakyThrows
  @Test
  void shouldPublishUpdatedTlrSettings() {
    TlrSettingsEntity tlrSettingsEntity = new TlrSettingsEntity(UUID.randomUUID(), true);
    wireMockServer.stubFor(post(urlMatching(String.format(PUBLICATIONS_URL_PATTERN, CONSORTIUM_ID)))
      .willReturn(okJson( "{\"id\": \"" + UUID.randomUUID() + "\",\"status\": \"IN_PROGRESS\"}")));
    when(tlrSettingsRepository.findAll(any(PageRequest.class)))
      .thenReturn(new PageImpl<>(List.of(tlrSettingsEntity)));
    when(tlrSettingsRepository.save(any(TlrSettingsEntity.class)))
      .thenReturn(tlrSettingsEntity);
    doAnswer(invocation -> {
      ((Runnable) invocation.getArguments()[1]).run();
      return null;
    }).when(systemUserScopedExecutionService).executeAsyncSystemUserScoped(anyString(),
      any(Runnable.class));

    mockUserTenants();
    mockConsortiaTenants();

    TlrSettings tlrSettings = new TlrSettings();
    tlrSettings.ecsTlrFeatureEnabled(true);
    tlrSettingsController.putTlrSettings(tlrSettings);

    wireMockServer.verify(1, postRequestedFor(urlMatching(String.format(PUBLICATIONS_URL_PATTERN, CONSORTIUM_ID)))
      .withRequestBody(equalToJson("""
            {
               "url": "/circulation/settings",
               "method": "POST",
               "tenants": ["college", "university"],
               "payload": {"name":"ecsTlrFeature","value":{"enabled":true}}
            }
        """)));
  }
}
