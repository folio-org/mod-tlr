package org.folio.service.impl;

import static org.folio.support.kafka.EventType.UPDATE;

import java.util.Objects;
import java.util.UUID;
import java.util.stream.Stream;

import org.folio.client.feign.CirculationItemClient;
import org.folio.client.feign.DcbEcsTransactionClient;
import org.folio.domain.dto.CirculationItem;
import org.folio.domain.dto.DcbItem;
import org.folio.domain.dto.DcbTransaction;
import org.folio.domain.dto.Item;
import org.folio.domain.entity.EcsTlrEntity;
import org.folio.repository.EcsTlrRepository;
import org.folio.service.KafkaEventHandler;
import org.folio.spring.service.SystemUserScopedExecutionService;
import org.folio.support.kafka.KafkaEvent;
import org.springframework.stereotype.Service;

import lombok.AllArgsConstructor;
import lombok.extern.log4j.Log4j2;

@AllArgsConstructor
@Service
@Log4j2
public class ItemEventHandler implements KafkaEventHandler<Item> {
  private final CirculationItemClient circulationItemClient;
  private final EcsTlrRepository ecsTlrRepository;
  private final DcbEcsTransactionClient dcbEcsTransactionClient;
  private final SystemUserScopedExecutionService executionService;

  @Override
  public void handle(KafkaEvent<Item> event) {
    log.info("handle:: processing item event: {}", event::getId);
    if (event.getGenericType() == UPDATE) {
      handleUpdateEvent(event);
    } else {
      log.info("handle:: ignoring event {} of unsupported type: {}", event::getId, event::getGenericType);
    }
    log.info("handle:: item event processed: {}", event::getId);
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
    var ecsRequests = ecsTlrRepository.findByItemId(UUID.fromString(item.getId()));

    // Update circulation items in tenants with primary and intermediate requests
    ecsRequests.stream()
      .flatMap(r -> Stream.of(r.getPrimaryRequestTenantId(), r.getIntermediateRequestTenantId()))
      .filter(Objects::nonNull)
      .distinct()
      .forEach(tenant -> updateCirculationItemInTenant(tenant, item.getId(), item.getBarcode()));

    // Update DCB transactions
    ecsTlrRepository.findByItemId(UUID.fromString(item.getId()))
      .forEach(request -> updateDcbTransactionsBarcode(request, item));
  }

  private void updateCirculationItemInTenant(String tenantId, String itemId, String itemBarcode) {
    executionService.executeAsyncSystemUserScoped(tenantId,
      () -> {
        log.info("updateCirculationItemInTenant:: updating circulation item {} in tenant {}",
          itemId, tenantId);
        var circulationItems = circulationItemClient.getCirculationItems("barcode==" + itemId);

        if (circulationItems.getTotalRecords() == 0) {
          log.info("updateCirculationItemInTenant:: circulation item {} not found in tenant {}",
            tenantId, itemId);
          return;
        }

        CirculationItem circulationItem = circulationItems.getItems().getFirst();
        var circulationItemId = circulationItem.getId().toString();
        log.info("updateCirculationItemInTenant:: found circulation item {} in tenant {}, updating",
          circulationItemId, tenantId);

        circulationItemClient.updateCirculationItem(circulationItemId,
          circulationItem.barcode(itemBarcode));

        log.info("updateCirculationItemInTenant:: updated circulation item {} with barcode {}",
          circulationItemId, itemBarcode);
      });
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
      dcbEcsTransactionClient.updateTransaction(transactionId.toString(),
        new DcbTransaction().item(new DcbItem().barcode(item.getBarcode()))));
  }
}
