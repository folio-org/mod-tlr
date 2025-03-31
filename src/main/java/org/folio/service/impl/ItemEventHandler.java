package org.folio.service.impl;

import static org.folio.support.KafkaEvent.EventType.UPDATED;

import org.folio.client.feign.CirculationItemClient;
import org.folio.domain.dto.CirculationItem;
import org.folio.domain.dto.Item;
import org.folio.service.KafkaEventHandler;
import org.folio.support.KafkaEvent;
import org.springframework.stereotype.Service;

import lombok.AllArgsConstructor;
import lombok.extern.log4j.Log4j2;

@AllArgsConstructor
@Service
@Log4j2
public class ItemEventHandler  implements KafkaEventHandler<Item> {
  private final CirculationItemClient circulationItemClient;

  @Override
  public void handle(KafkaEvent<Item> event) {
    log.info("handle:: processing item event: {}", event::getId);
    if (event.getType() == UPDATED) {
      handleUpdateEvent(event);
    } else {
      log.info("handle:: ignoring event {} of unsupported type: {}", event::getId, event::getType);
    }
    log.info("handle:: item event processed: {}", event::getId);
  }

  private void handleUpdateEvent(KafkaEvent<Item> event) {
    Item oldItem = event.getData().getOldVersion();
    Item newItem = event.getData().getNewVersion();

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
      "Item ID: {}", newItem.getId());
  }

  private void handleAddedBarcodeEvent(KafkaEvent<Item> event) {
    handleAddedBarcodeEvent(event.getData().getNewVersion(), event.getTenant());
  }

  private void handleAddedBarcodeEvent(Item item, String tenantId) {
    var circulationItems = circulationItemClient.getCirculationItems(
      "barcode==" + item.getBarcode());

    if (circulationItems.getTotalRecords() == 0) {
      log.info("handleAddedBarcodeEvent:: circulation item not found, ID: {}", item.getId());
    }

    log.info("handleAddedBarcodeEvent:: found {} circulation items, updating", item.getId());

    CirculationItem circulationItem = circulationItems.getItems().getFirst();
    log.info("handleAddedBarcodeEvent:: found circulation item {}, updating",
      circulationItem.getId());

    circulationItemClient.updateCirculationItem(circulationItem.getId().toString(),
      circulationItem.barcode(item.getBarcode()));

    log.info("handleAddedBarcodeEvent:: updated circulation item {} with barcode {}",
      circulationItem.getId(), item.getBarcode());
  }
}
