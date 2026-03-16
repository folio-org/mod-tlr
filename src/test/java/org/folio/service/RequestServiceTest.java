package org.folio.service;

import static org.folio.util.TestUtils.mockFolioExecutionContextService;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Optional;
import java.util.UUID;

import org.folio.client.CirculationItemClient;
import org.folio.client.InstanceClient;
import org.folio.client.ItemClient;
import org.folio.domain.dto.CirculationItem;
import org.folio.domain.dto.CirculationItemStatus;
import org.folio.domain.dto.Instance;
import org.folio.domain.dto.Item;
import org.folio.domain.dto.ItemStatus;
import org.folio.domain.dto.Request;
import org.folio.domain.entity.EcsTlrEntity;
import org.folio.service.impl.RequestServiceImpl;
import org.folio.spring.scope.FolioExecutionContextService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class RequestServiceTest {
  @MockitoBean
  private CirculationItemClient circulationItemClient;
  @MockitoBean
  private ItemClient itemClient;
  @MockitoBean
  private InstanceClient instanceClient;
  @MockitoBean
  private FolioExecutionContextService contextService;
  @MockitoSpyBean
  private RequestServiceImpl requestService;
  private EcsTlrEntity ecsTlrEntity;
  private Request secondaryRequest;
  private static final String ITEM_ID = UUID.randomUUID().toString();
  private static final String INSTANCE_ID = UUID.randomUUID().toString();
  private static final String LENDER_ID = UUID.randomUUID().toString();
  private static final String HOLDINGS_RECORD_ID = "10cd3a5a-d36f-4c7a-bc4f-e1ae3cf820c9";
  private static final String LENDING_LIBRARY_CODE = "TEST_CODE";
  private static final String DCB_LOCATION_ID = "9d1b77e8-f02e-4b7f-b296-3f2042ddac54";

  @BeforeEach
  void setUp() {
    ecsTlrEntity = new EcsTlrEntity();
    secondaryRequest = new Request().itemId(ITEM_ID).instanceId(INSTANCE_ID);
    mockFolioExecutionContextService(contextService);
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
  void shouldReturnExistingCirculationItemWhenStatusMatches() {
    when(itemClient.get(ITEM_ID)).thenReturn(
      new Item().status(new ItemStatus().name(ItemStatus.NameEnum.AVAILABLE))
    );
    CirculationItem existingItem = new CirculationItem()
      .status(new CirculationItemStatus().name(CirculationItemStatus.NameEnum.AVAILABLE));
    when(circulationItemClient.getCirculationItem(any())).thenReturn(Optional.of(existingItem));

    assertEquals(existingItem, requestService.createCirculationItem(secondaryRequest, LENDER_ID));
  }

  @Test
  void shouldUpdateExistingCirculationItemWhenStatusDiverges() {
    when(itemClient.get(ITEM_ID)).thenReturn(
      new Item().status(new ItemStatus().name(ItemStatus.NameEnum.PAGED))
    );
    CirculationItem existingItem = new CirculationItem().id(UUID.randomUUID());
    when(circulationItemClient.getCirculationItem(any())).thenReturn(Optional.of(existingItem));
    CirculationItem updatedItem = new CirculationItem().id(UUID.randomUUID())
      .status(new CirculationItemStatus().name(CirculationItemStatus.NameEnum.AVAILABLE));
    when(circulationItemClient.updateCirculationItem(anyString(), any())).thenReturn(updatedItem);

    assertEquals(updatedItem, requestService.createCirculationItem(secondaryRequest, LENDER_ID));
  }

  @Test
  void shouldCreateCirculationItem() {
    when(circulationItemClient.getCirculationItem(any())).thenReturn(Optional.empty());
    when(circulationItemClient.createCirculationItem(any(), any())).thenReturn(new CirculationItem());

    Item item = new Item();
    item.setStatus(new ItemStatus(ItemStatus.NameEnum.PAGED));
    when(itemClient.get(ITEM_ID)).thenReturn(item);

    String instanceTitle = "Title";
    Instance instance = new Instance();
    instance.setTitle(instanceTitle);
    when(instanceClient.get(INSTANCE_ID)).thenReturn(instance);

    CirculationItem expectedCirculationItem = new CirculationItem()
      .status(new CirculationItemStatus()
        .name(CirculationItemStatus.NameEnum.PAGED))
      .id(UUID.fromString(ITEM_ID))
      .holdingsRecordId(UUID.fromString(HOLDINGS_RECORD_ID))
      .dcbItem(true)
      .instanceTitle(instanceTitle)
      .effectiveLocationId(DCB_LOCATION_ID)
      .lendingLibraryCode(LENDING_LIBRARY_CODE);
    requestService.createCirculationItem(secondaryRequest, LENDER_ID);
    verify(circulationItemClient).createCirculationItem(ITEM_ID, expectedCirculationItem);
  }

  @Test
  void circulationItemUpdateShouldBeSkippedWhenNull() {
    assertNull(requestService.updateCirculationItemOnRequestCreation(null, null));
  }
}
