package org.folio.service;

import static java.util.Collections.emptyList;
import static java.util.UUID.randomUUID;
import static org.folio.support.kafka.EventType.UPDATE;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.util.EnumSet;
import java.util.List;
import java.util.UUID;

import org.folio.api.BaseIT;
import org.folio.client.CirculationItemClient;
import org.folio.client.DcbEcsTransactionClient;
import org.folio.domain.dto.CirculationItem;
import org.folio.domain.dto.CirculationItems;
import org.folio.domain.dto.DcbItem;
import org.folio.domain.dto.DcbTransaction;
import org.folio.domain.dto.Item;
import org.folio.domain.entity.EcsTlrEntity;
import org.folio.repository.EcsTlrRepository;
import org.folio.service.impl.ItemEventHandler;
import org.folio.spring.FolioExecutionContext;
import org.folio.spring.scope.FolioExecutionContextService;
import org.folio.support.kafka.EventType;
import org.folio.support.kafka.InventoryKafkaEvent;
import org.folio.support.kafka.KafkaEvent;
import org.folio.util.TestUtils;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

class ItemEventHandlerTest extends BaseIT {
  private static final EnumSet<EventType> SUPPORTED_EVENT_TYPES = EnumSet.of(UPDATE);
  private static final String NEW_BARCODE = "new_barcode";

  @MockitoBean
  private CirculationItemClient circulationItemClient;
  @MockitoBean
  private EcsTlrRepository ecsTlrRepository;
  @MockitoBean
  private FolioExecutionContextService contextService;
  @MockitoBean
  private DcbEcsTransactionClient dcbEcsTransactionClient;
  @Autowired
  private ItemEventHandler itemEventHandler;

  @BeforeEach
  void beforeEach() {
    TestUtils.mockFolioExecutionContextService(contextService);
  }

  @ParameterizedTest
  @EnumSource(EventType.class)
  void eventsOfUnsupportedTypesAreIgnored(EventType eventType) {
    if (!SUPPORTED_EVENT_TYPES.contains(eventType)) {
      itemEventHandler.handle(new InventoryKafkaEvent<>(null, null,
        InventoryKafkaEvent.InventoryKafkaEventType.UPDATE, null, null));
      verifyNoInteractions(ecsTlrRepository, circulationItemClient);
    }
  }

  @Test
  void itemUpdateEventIgnoredWhenBarcodeHasNotBeenAdded() {
    UUID itemId = randomUUID();
    var item = new Item().id(itemId.toString());

    var event = createItemUpdateEvent(item, item);
    itemEventHandler.handle(event);

    verifyNoInteractions(ecsTlrRepository);
    verifyNoInteractions(circulationItemClient);
  }

  @Test
  void itemUpdateEventProcessedWhenBarcodeAdded() {
    var itemId = randomUUID();
    var oldItem = new Item().id(itemId.toString());
    var newItem = new Item().id(itemId.toString()).barcode(NEW_BARCODE);

    when(ecsTlrRepository.findByItemId(itemId)).thenReturn(emptyList());

    var event = createItemUpdateEvent(oldItem, newItem);
    itemEventHandler.handle(event);

    verify(ecsTlrRepository).findByItemId(itemId);
    verifyNoInteractions(circulationItemClient);
  }

  @Test
  void circulationItemsAndTransactionsUpdatedWhenBarcodeAdded() {
    var itemId = randomUUID();
    var oldItem = new Item().id(itemId.toString());
    var newItem = new Item().id(itemId.toString()).barcode(NEW_BARCODE);

    var escRequest = new EcsTlrEntity();
    var primaryRequestDcbTransactionId = UUID.randomUUID();
    var secondaryRequestDcbTransactionId = UUID.randomUUID();
    var intermediateRequestDcbTransactionId = UUID.randomUUID();
    escRequest.setPrimaryRequestTenantId(TENANT_ID_UNIVERSITY);
    escRequest.setPrimaryRequestDcbTransactionId(primaryRequestDcbTransactionId);
    escRequest.setSecondaryRequestTenantId(TENANT_ID_COLLEGE);
    escRequest.setSecondaryRequestDcbTransactionId(secondaryRequestDcbTransactionId);
    escRequest.setIntermediateRequestTenantId(TENANT_ID_CONSORTIUM);
    escRequest.setIntermediateRequestDcbTransactionId(intermediateRequestDcbTransactionId);

    var circulationItem = new CirculationItem().id(itemId);
    var circulationItems = new CirculationItems()
      .items(List.of(circulationItem))
      .totalRecords(1);

    when(ecsTlrRepository.findByItemId(itemId)).thenReturn(List.of(escRequest));
    when(circulationItemClient.getCirculationItems(any())).thenReturn(circulationItems);

    var event = createItemUpdateEvent(oldItem, newItem);
    itemEventHandler.handle(event);

    var dcbTransactionPatch = new DcbTransaction().item(new DcbItem().barcode(NEW_BARCODE));

    verify(ecsTlrRepository).findByItemId(itemId);
    verify(contextService).execute(eq(TENANT_ID_UNIVERSITY), any(FolioExecutionContext.class), any(Runnable.class));
    verify(contextService).execute(eq(TENANT_ID_CONSORTIUM), any(FolioExecutionContext.class), any(Runnable.class));
    verify(circulationItemClient, times(2))
      .updateCirculationItem(itemId.toString(), circulationItem);
    verify(dcbEcsTransactionClient).updateTransaction(primaryRequestDcbTransactionId.toString(),
      dcbTransactionPatch);
    verify(dcbEcsTransactionClient).updateTransaction(secondaryRequestDcbTransactionId.toString(),
      dcbTransactionPatch);
    verify(dcbEcsTransactionClient).updateTransaction(
      intermediateRequestDcbTransactionId.toString(), dcbTransactionPatch);
  }

  private static KafkaEvent<Item> createItemUpdateEvent(Item oldItem, Item newItem) {
    return new InventoryKafkaEvent<>(randomUUID().toString(), "test_tenant",
      InventoryKafkaEvent.InventoryKafkaEventType.UPDATE, oldItem, newItem)
      .withTenantIdHeaderValue("test_tenant")
      .withUserIdHeaderValue("test_user");
  }
}
