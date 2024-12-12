package org.folio.service.impl;

import static org.folio.domain.dto.ItemStatus.NameEnum.AWAITING_DELIVERY;
import static org.folio.domain.dto.ItemStatus.NameEnum.CHECKED_OUT;
import static org.folio.domain.dto.ItemStatus.NameEnum.IN_PROCESS;
import static org.folio.domain.dto.ItemStatus.NameEnum.IN_TRANSIT;
import static org.folio.domain.dto.ItemStatus.NameEnum.MISSING;
import static org.folio.domain.dto.ItemStatus.NameEnum.ON_ORDER;
import static org.folio.domain.dto.ItemStatus.NameEnum.PAGED;
import static org.folio.domain.dto.ItemStatus.NameEnum.RESTRICTED;
import static org.folio.domain.dto.Request.RequestTypeEnum.HOLD;
import static org.folio.domain.dto.Request.StatusEnum.OPEN_NOT_YET_FILLED;

import java.util.EnumSet;

import org.folio.domain.dto.ItemStatus;
import org.folio.service.AddressTypeService;
import org.folio.service.ConsortiaService;
import org.folio.service.DepartmentService;
import org.folio.service.InventoryService;
import org.folio.service.LocationService;
import org.folio.service.RequestService;
import org.folio.service.ServicePointService;
import org.folio.service.UserGroupService;
import org.folio.service.UserService;
import org.folio.spring.service.SystemUserScopedExecutionService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import lombok.extern.log4j.Log4j2;

@Service
@Log4j2
public class SearchSlipsService extends StaffSlipsServiceImpl {

  private static final EnumSet<ItemStatus.NameEnum> ITEM_STATUSES = EnumSet.of(
    CHECKED_OUT, AWAITING_DELIVERY, IN_TRANSIT, MISSING, PAGED, ON_ORDER, IN_PROCESS, RESTRICTED);

  @Autowired
  public SearchSlipsService(LocationService locationService, InventoryService inventoryService,
    RequestService requestService, ConsortiaService consortiaService,
    SystemUserScopedExecutionService executionService, UserService userService,
    UserGroupService userGroupService, DepartmentService departmentService,
    AddressTypeService addressTypeService, ServicePointService servicePointService) {

    super(ITEM_STATUSES, EnumSet.of(OPEN_NOT_YET_FILLED), EnumSet.of(HOLD), locationService,
      inventoryService, requestService, consortiaService, executionService, userService,
      userGroupService, departmentService, addressTypeService, servicePointService);
  }
}
