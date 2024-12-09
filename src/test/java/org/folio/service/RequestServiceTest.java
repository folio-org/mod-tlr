package org.folio.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.UUID;

import org.folio.client.feign.CirculationItemClient;
import org.folio.domain.dto.CirculationItem;
import org.folio.domain.dto.CirculationItemStatus;
import org.folio.domain.dto.InventoryInstance;
import org.folio.domain.dto.InventoryItem;
import org.folio.domain.dto.InventoryItemStatus;
import org.folio.domain.dto.Request;
import org.folio.domain.entity.EcsTlrEntity;
import org.folio.service.impl.RequestServiceImpl;
import org.folio.spring.service.SystemUserScopedExecutionService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.boot.test.mock.mockito.SpyBean;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class RequestServiceTest {
  @MockBean
  private CirculationItemClient circulationItemClient;
  @MockBean
  private SystemUserScopedExecutionService systemUserScopedExecutionService;
  @SpyBean
  private RequestServiceImpl requestService;
  private EcsTlrEntity ecsTlrEntity;
  private Request secondaryRequest;
  private static final String ITEM_ID = UUID.randomUUID().toString();
  private static final String INSTANCE_ID = UUID.randomUUID().toString();
  private static final String BORROWER_ID = UUID.randomUUID().toString();
  private static final String LENDER_ID = UUID.randomUUID().toString();
  private static final String HOLDINGS_RECORD_ID = "10cd3a5a-d36f-4c7a-bc4f-e1ae3cf820c9";
  private static final String LENDING_LIBRARY_CODE = "TEST_CODE";

  @BeforeEach
  void setUp() {
    ecsTlrEntity = new EcsTlrEntity();
    secondaryRequest = new Request().itemId(ITEM_ID).instanceId(INSTANCE_ID);
    doAnswer(invocation -> {
      ((Runnable) invocation.getArguments()[1]).run();
      return null;
    }).when(systemUserScopedExecutionService).executeAsyncSystemUserScoped(anyString(),
      any(Runnable.class));
  }

  @Test
  void shouldReturnNullIfRequestIsNull() {
    assertNull(requestService.createCirculationItem(null, LENDER_ID));
  }

  @Test
  void shouldReturnNullIfItemIdOrInstanceIdIsNull() {
    secondaryRequest.setItemId(null);
    assertNull(requestService.createCirculationItem(secondaryRequest, LENDER_ID));

    secondaryRequest.setItemId(ITEM_ID);
    secondaryRequest.setInstanceId(null);
    assertNull(requestService.createCirculationItem(secondaryRequest, LENDER_ID));
  }

  @Test
  void shouldReturnExistingCirculationItemIfFound() {
    CirculationItem existingItem = new CirculationItem();
    when(circulationItemClient.getCirculationItem(any())).thenReturn(existingItem);

    assertEquals(existingItem, requestService.createCirculationItem(secondaryRequest, LENDER_ID));
  }

  @Test
  void shouldCreateCirculationItem() {
    when(circulationItemClient.getCirculationItem(any())).thenReturn(null);
    when(circulationItemClient.createCirculationItem(any(), any())).thenReturn(new CirculationItem());

    InventoryItem item = new InventoryItem();
    item.setStatus(new InventoryItemStatus((InventoryItemStatus.NameEnum.PAGED)));
    when(requestService.getItemFromStorage(eq(ITEM_ID), anyString())).thenReturn(item);

    String instanceTitle = "Title";
    InventoryInstance instance = new InventoryInstance();
    instance.setTitle(instanceTitle);
    when(requestService.getInstanceFromStorage(eq(INSTANCE_ID), anyString())).thenReturn(instance);

    CirculationItem expectedCirculationItem = new CirculationItem()
      .status(new CirculationItemStatus()
        .name(CirculationItemStatus.NameEnum.AVAILABLE))
      .id(UUID.fromString(ITEM_ID))
      .holdingsRecordId(UUID.fromString(HOLDINGS_RECORD_ID))
      .dcbItem(true)
      .instanceTitle(instanceTitle)
      .lendingLibraryCode(LENDING_LIBRARY_CODE);
    requestService.createCirculationItem(secondaryRequest, LENDER_ID);
    verify(circulationItemClient).createCirculationItem(ITEM_ID, expectedCirculationItem);
  }

  @Test
  void circulationItemUpdateShouldBeSkippedWhenNull() {
    assertNull(requestService.updateCirculationItemOnRequestCreation(null, null));
  }
}
