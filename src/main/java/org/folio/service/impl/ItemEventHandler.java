package org.folio.service.impl;

import static org.folio.support.KafkaEvent.EventType.UPDATE;

import java.util.UUID;

import org.folio.client.feign.CirculationItemClient;
import org.folio.client.feign.DcbTransactionClient;
import org.folio.domain.dto.CirculationItem;
import org.folio.domain.dto.DcbUpdateItem;
import org.folio.domain.dto.DcbUpdateTransaction;
import org.folio.domain.dto.Item;
import org.folio.domain.entity.EcsTlrEntity;
import org.folio.repository.EcsTlrRepository;
import org.folio.service.KafkaEventHandler;
import org.folio.service.TenantService;
import org.folio.spring.service.SystemUserScopedExecutionService;
import org.folio.support.KafkaEvent;
import org.springframework.stereotype.Service;

import lombok.AllArgsConstructor;
import lombok.extern.log4j.Log4j2;

@AllArgsConstructor
@Service
@Log4j2
public class ItemEventHandler implements KafkaEventHandler<Item> {
  private final CirculationItemClient circulationItemClient;
  private final EcsTlrRepository ecsTlrRepository;
  private final DcbTransactionClient dcbTransactionClient;
  private final TenantService tenantService;
  private final SystemUserScopedExecutionService executionService;

  @Override
  public void handle(KafkaEvent<Item> event) {
    log.info("handle:: processing item event: {}", event::getEventId);
    if (event.getType() == UPDATE) {
      handleUpdateEvent(event);
    } else {
      log.info("handle:: ignoring event {} of unsupported type: {}", event::getEventId, event::getType);
    }
    log.info("handle:: item event processed: {}", event::getEventId);
  }

  private void handleUpdateEvent(KafkaEvent<Item> event) {
    Item oldItem = event.getOldVersion();
    Item newItem = event.getNewVersion();

    if (oldItem == null || newItem == null) {
      log.warn("handle:: event update message is missing either old or new item info. " +
        "Old item is null: {}. New item is null: {}.", oldItem == null, newItem == null);
      return;
    }

    if ((oldItem.getBarcode() == null || oldItem.getBarcode().isBlank())
      && newItem.getBarcode() != null && !newItem.getBarcode().isBlank()) {

      log.info("handle:: item without a barcode updated, new barcode: {}", newItem.getBarcode());

      handleAddedBarcodeEvent(event);
    }

    log.debug("handleUpdateEvent:: ignoring item update - barcode info hasn't changed. " +
      "Item ID: {}", newItem::getId);
  }

  private void handleAddedBarcodeEvent(KafkaEvent<Item> event) {
    handleAddedBarcodeEvent(event.getNewVersion(), event.getTenant());
  }

  private void handleAddedBarcodeEvent(Item item, String tenantId) {
    // Update circulation item

    var circulationItems = circulationItemClient.getCirculationItems(
      "barcode==" + item.getId());

    if (circulationItems.getTotalRecords() == 0) {
      log.info("handleAddedBarcodeEvent:: circulation item not found, ID: {}", item::getId);
      return;
    }

    CirculationItem circulationItem = circulationItems.getItems().getFirst();
    log.info("handleAddedBarcodeEvent:: found circulation item {}, updating",
      circulationItem::getId);

    circulationItemClient.updateCirculationItem(circulationItem.getId().toString(),
      circulationItem.barcode(item.getBarcode()));

    log.info("handleAddedBarcodeEvent:: updated circulation item {} with barcode {}",
      circulationItem::getId, item::getBarcode);

    // Update DCB transactions

    ecsTlrRepository.findByItemId(UUID.fromString(item.getId()))
      .forEach(request -> updateDcbTransactionsBarcode(request, item));
  }

  private void updateDcbTransactionsBarcode(EcsTlrEntity ecsRequest, Item item) {
    updateDcbTransactionBarcode(ecsRequest.getPrimaryRequestTenantId(),
      ecsRequest.getPrimaryRequestDcbTransactionId(), item);

    updateDcbTransactionBarcode(ecsRequest.getSecondaryRequestTenantId(),
      ecsRequest.getSecondaryRequestDcbTransactionId(), item);

    updateDcbTransactionBarcode(ecsRequest.getIntermediateRequestTenantId(),
      ecsRequest.getIntermediateRequestDcbTransactionId(), item);
  }

  private void updateDcbTransactionBarcode(String tenantId, UUID transactionId, Item item) {
    if (tenantId == null || transactionId == null) {
      log.info("updateDcbTransactionBarcode:: tenantId is {}, transactionId is {}, skipping", tenantId, transactionId);
      return;
    }

    log.info("updateDcbTransactionBarcode:: tenant: {}, transaction ID: {}, item barcode: {}",
      tenantId, transactionId, item.getBarcode());

    executionService.executeSystemUserScoped(tenantId, () ->
      dcbTransactionClient.updateDcbTransaction(transactionId.toString(),
      new DcbUpdateTransaction()
        .item(new DcbUpdateItem()
          .materialType(item.getMaterialTypeId())
          .barcode(item.getBarcode())
          .lendingLibraryCode("some_code")
        )));
  }
}
