package org.folio.service.impl;

import org.folio.domain.dto.ExtendedInstance;
import org.folio.service.InventoryService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import lombok.extern.log4j.Log4j2;

@Log4j2
@Service
public class ExtendedInstanceCloningServiceImpl extends CloningServiceImpl<ExtendedInstance> {

  public static final String CONSORTIUM_FOLIO_INSTANCE_SOURCE = "CONSORTIUM-FOLIO";
  public static final String CONSORTIUM_MARC_INSTANCE_SOURCE = "CONSORTIUM-MARC";
  public static final String CONSORTIUM_LINKED_DATA_INSTANCE_SOURCE = "CONSORTIUM-LINKED_DATA";

  private final InventoryService inventoryService;

  public ExtendedInstanceCloningServiceImpl(@Autowired InventoryService inventoryService) {
    super(ExtendedInstance::getId);
    this.inventoryService = inventoryService;
  }

  @Override
  protected ExtendedInstance find(String instanceId) {
    return inventoryService.findExtendedInstance(instanceId);
  }

  @Override
  protected ExtendedInstance create(ExtendedInstance clone) {
    return inventoryService.createExtendedInstance(clone);
  }

  @Override
  protected ExtendedInstance buildClone(ExtendedInstance instance) {
    String originalSource = instance.getSource();
    String newSource = switch (originalSource.toLowerCase()) {
      case "folio" -> CONSORTIUM_FOLIO_INSTANCE_SOURCE;
      case "marc" -> CONSORTIUM_MARC_INSTANCE_SOURCE;
      case "linked_data" -> CONSORTIUM_LINKED_DATA_INSTANCE_SOURCE;
      default -> null;
    };

    if (newSource != null) {
      log.info("buildClone:: replacing source '{}' with '{}'", originalSource, newSource);
      instance.setSource(newSource);
    }

    log.debug("buildClone:: result: {}", () -> instance);
    return instance;
  }
}
