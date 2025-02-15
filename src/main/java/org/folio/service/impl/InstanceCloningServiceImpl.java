package org.folio.service.impl;

import org.folio.domain.dto.Instance;
import org.folio.service.InventoryService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import lombok.extern.log4j.Log4j2;

@Log4j2
@Service
public class InstanceCloningServiceImpl extends CloningServiceImpl<Instance> {

  public static final String CONSORTIUM_FOLIO_INSTANCE_SOURCE = "CONSORTIUM-FOLIO";
  public static final String CONSORTIUM_MARC_INSTANCE_SOURCE = "CONSORTIUM-MARC";
  public static final String CONSORTIUM_LINKED_DATA_INSTANCE_SOURCE = "CONSORTIUM-LINKED_DATA";

  private final InventoryService inventoryService;

  public InstanceCloningServiceImpl(@Autowired InventoryService inventoryService) {
    super(Instance::getId);
    this.inventoryService = inventoryService;
  }

  @Override
  protected Instance find(String instanceId) {
    return inventoryService.findInstance(instanceId);
  }

  @Override
  protected Instance create(Instance clone) {
    return inventoryService.createInstance(clone);
  }

  @Override
  protected Instance buildClone(Instance instance) {
    String source = instance.getSource();
    String newSource = switch (source.toLowerCase()) {
      case "folio" -> CONSORTIUM_FOLIO_INSTANCE_SOURCE;
      case "marc" -> CONSORTIUM_MARC_INSTANCE_SOURCE;
      case "linked_data" -> CONSORTIUM_LINKED_DATA_INSTANCE_SOURCE;
      default -> null;
    };

    if (newSource != null) {
      instance.setSource(newSource);
    }

    log.debug("buildClone:: result: {}", () -> instance);
    return instance;
  }
}
