package org.folio.service;

import static java.util.Collections.emptyList;
import static java.util.Collections.singletonList;
import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.Arrays;
import java.util.List;
import java.util.UUID;
import java.util.stream.Stream;

import org.folio.client.feign.SearchClient;
import org.folio.domain.dto.Instance;
import org.folio.domain.dto.Item;
import org.folio.domain.dto.ItemStatus;
import org.folio.domain.dto.SearchInstancesResponse;
import org.folio.domain.entity.EcsTlrEntity;
import org.folio.service.impl.TenantServiceImpl;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class TenantServiceTest {
  private static final UUID INSTANCE_ID = UUID.randomUUID();

  @Mock
  private SearchClient searchClient;
  @InjectMocks
  private TenantServiceImpl tenantService;

  @ParameterizedTest
  @MethodSource("parametersForGetLendingTenants")
  void getLendingTenants(List<String> expectedTenantIds, Instance instance) {
    Mockito.when(searchClient.searchInstance(Mockito.any()))
      .thenReturn(new SearchInstancesResponse().instances(singletonList(instance)));
    EcsTlrEntity ecsTlr = new EcsTlrEntity();
    ecsTlr.setInstanceId(INSTANCE_ID);
    assertEquals(expectedTenantIds, tenantService.getLendingTenants(ecsTlr));
  }

  private static Stream<Arguments> parametersForGetLendingTenants() {
    return Stream.of(
      Arguments.of(emptyList(), null),

      // instances without items are ignored
      Arguments.of(emptyList(), buildInstance()),

      // items without tenantId are ignored
      Arguments.of(emptyList(), buildInstance(buildItem(null, "Available"))),
      Arguments.of(List.of("a"), buildInstance(
        buildItem(null, "Available"),
        buildItem("a", "Paged")
      )),

      // 1 tenant, 1 item
      Arguments.of(List.of("a"), buildInstance(buildItem("a", "Available"))),
      Arguments.of(List.of("a"), buildInstance(buildItem("a", "Checked out"))),
      Arguments.of(List.of("a"), buildInstance(buildItem("a", "In transit"))),
      Arguments.of(List.of("a"), buildInstance(buildItem("a", "Paged"))),

      // multiple tenants, same item status, tenants should be sorted by number of items
      Arguments.of(List.of("b", "c", "a"), buildInstance(
        buildItem("a", "Available"),
        buildItem("b", "Available"),
        buildItem("b", "Available"),
        buildItem("b", "Available"),
        buildItem("c", "Available"),
        buildItem("c", "Available")
      )),
      Arguments.of(List.of("a", "c", "b"), buildInstance(
        buildItem("a", "Checked out"),
        buildItem("a", "Checked out"),
        buildItem("a", "Checked out"),
        buildItem("b", "Checked out"),
        buildItem("c", "Checked out"),
        buildItem("c", "Checked out")
      )),
      Arguments.of(List.of("b", "c", "a"), buildInstance(
        buildItem("a", "In transit"),
        buildItem("b", "In transit"),
        buildItem("b", "In transit"),
        buildItem("b", "In transit"),
        buildItem("c", "In transit"),
        buildItem("c", "In transit")
      )),
      Arguments.of(List.of("c", "b", "a"), buildInstance(
        buildItem("a", "Paged"),
        buildItem("b", "Paged"),
        buildItem("b", "Paged"),
        buildItem("c", "Paged"),
        buildItem("c", "Paged"),
        buildItem("c", "Paged")
      )),

      // item priority test: "Available" > ("Checked out" + "In transit") > all others
      Arguments.of(List.of("b", "c", "a"), buildInstance(
        buildItem("a", "Paged"),
        buildItem("a", "Awaiting pickup"),
        buildItem("a", "Awaiting delivery"),
        buildItem("b", "Available"),
        buildItem("c", "Checked out"),
        buildItem("c", "In transit")
      )),
      Arguments.of(List.of("c", "b", "a"), buildInstance(
        buildItem("a", "Paged"),
        buildItem("a", "Awaiting pickup"),
        buildItem("a", "Awaiting delivery"),
        buildItem("a", "Missing"),
        buildItem("a", "On order"),
        buildItem("b", "Checked out"),
        buildItem("b", "Checked out"),
        buildItem("b", "Checked out"),
        buildItem("c", "Checked out"),
        buildItem("c", "Checked out"),
        buildItem("c", "In transit"),
        buildItem("c", "In transit")
      )),
      Arguments.of(List.of("a", "c", "b"), buildInstance(
        buildItem("a", "Paged"),
        buildItem("a", "Awaiting pickup"),
        buildItem("a", "Awaiting delivery"),
        buildItem("b", "Paged"),
        buildItem("c", "Paged"),
        buildItem("c", "Awaiting pickup")
      ))
    );
  }

  private static Instance buildInstance(Item... items) {
    return new Instance()
      .id(INSTANCE_ID.toString())
      .tenantId("centralTenant")
      .items(Arrays.stream(items).toList());
  }

  private static Item buildItem(String tenantId, String status) {
    return new Item()
      .id(UUID.randomUUID().toString())
      .tenantId(tenantId)
      .status(new ItemStatus().name(status));
  }

}