package org.folio.service;

import static org.folio.util.TestUtils.randomId;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import org.folio.domain.dto.ExtendedInstance;
import org.folio.service.impl.ExtendedInstanceCloningServiceImpl;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import feign.FeignException;

@ExtendWith(MockitoExtension.class)
class ExtendedInstanceCloningServiceTest {

  @Mock
  private InventoryService inventoryService;

  @InjectMocks
  private ExtendedInstanceCloningServiceImpl extendedInstanceCloningService;

  @Captor
  private ArgumentCaptor<ExtendedInstance> instanceCaptor;

  @ParameterizedTest
  @CsvSource({
    "FoLiO, CONSORTIUM-FOLIO",
    "MARC, CONSORTIUM-MARC",
    "linked_data, CONSORTIUM-LINKED_DATA",
    "rAnDoM, rAnDoM"
  })
  void instanceDoesNotExistInTargetTenant(String originalSource, String expectedSource) {
    ExtendedInstance originalInstance = new ExtendedInstance()
      .id(randomId())
      .source(originalSource);

    when(inventoryService.findExtendedInstance(originalInstance.getId()))
      .thenThrow(FeignException.NotFound.class);
    when(inventoryService.createExtendedInstance(any(ExtendedInstance.class)))
      .thenReturn(originalInstance);

    extendedInstanceCloningService.clone(originalInstance);

    verify(inventoryService).findExtendedInstance(originalInstance.getId());
    verify(inventoryService).createExtendedInstance(instanceCaptor.capture());
    assertEquals(expectedSource, instanceCaptor.getValue().getSource());
  }

  @ParameterizedTest
  @ValueSource(strings = { "FoLiO", "MARC", "linked_data", "rAnDoM" })
  void instanceExistsInTargetTenant(String originalSource) {
    ExtendedInstance originalInstance = new ExtendedInstance()
      .id(randomId())
      .source(originalSource);

    when(inventoryService.findExtendedInstance(originalInstance.getId()))
      .thenReturn(originalInstance);

    ExtendedInstance clonedInstance = extendedInstanceCloningService.clone(originalInstance);

    assertEquals(originalSource, clonedInstance.getSource());
    verify(inventoryService).findExtendedInstance(originalInstance.getId());
    verify(inventoryService, times(0)).createExtendedInstance(any(ExtendedInstance.class));
  }
}