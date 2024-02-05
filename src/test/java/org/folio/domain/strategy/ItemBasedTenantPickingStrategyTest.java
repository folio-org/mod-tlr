package org.folio.domain.strategy;

import static java.util.Collections.emptyList;
import static java.util.Optional.ofNullable;
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
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class ItemBasedTenantPickingStrategyTest {
  private static final String INSTANCE_ID = UUID.randomUUID().toString();

  @Mock
  private SearchClient searchClient;
  @InjectMocks
  private ItemBasedTenantPickingStrategy strategy;

  @ParameterizedTest
  @MethodSource("parametersForPickTenant")
  void pickTenant(String expectedTenantId, List<Instance> instances) {
    Mockito.when(searchClient.searchInstance(Mockito.any()))
      .thenReturn(new SearchInstancesResponse().instances(instances));
    assertEquals(ofNullable(expectedTenantId), strategy.pickTenant(INSTANCE_ID));
  }

  private static Stream<Arguments> parametersForPickTenant() {
    return Stream.of(
      Arguments.of(null, emptyList()),

      // instances without items are ignored
      Arguments.of(null, List.of(buildInstance("a"))),

      // instances without tenantId are ignored
      Arguments.of(null, List.of(buildInstance(null, "Available"))),
      Arguments.of("a", List.of(
        buildInstance(null, "Available"),
        buildInstance("a", "Paged")
      )),

      // 1 tenant, 1 item
      Arguments.of("a", List.of(buildInstance("a", "Available"))),
      Arguments.of("a", List.of(buildInstance("a", "Checked out"))),
      Arguments.of("a", List.of(buildInstance("a", "In transit"))),
      Arguments.of("a", List.of(buildInstance("a", "Paged"))),

      // multiple tenants, same item status, tenant with most items wins
      Arguments.of("b", List.of(
        buildInstance("a", "Available"),
        buildInstance("b", "Available", "Available", "Available"),
        buildInstance("c", "Available", "Available")
      )),
      Arguments.of("b", List.of(
        buildInstance("a", "Checked out"),
        buildInstance("b", "Checked out", "Checked out", "Checked out"),
        buildInstance("c", "Checked out", "Checked out")
      )),
      Arguments.of("b", List.of(
        buildInstance("a", "In transit"),
        buildInstance("b", "In transit", "In transit", "In transit"),
        buildInstance("c", "In transit", "In transit")
      )),
      Arguments.of("b", List.of(
        buildInstance("a", "Paged"),
        buildInstance("b", "Paged", "Paged", "Paged"),
        buildInstance("c", "Paged", "Paged")
      )),

      // item priority test: "Available" > ("Checked out" + "In transit") > all others
      Arguments.of("b", List.of(
        buildInstance("a", "Paged", "Awaiting pickup", "Awaiting delivery"),
        buildInstance("b", "Available"),
        buildInstance("c", "Checked out", "In transit")
      )),
      Arguments.of("c", List.of(
        buildInstance("a", "Paged", "Awaiting pickup", "Awaiting delivery", "Missing", "On order"),
        buildInstance("b", "Checked out", "Checked out", "Checked out"),
        buildInstance("c", "Checked out", "Checked out", "In transit", "In transit")
      )),
      Arguments.of("a", List.of(
        buildInstance("a", "Paged", "Awaiting pickup", "Awaiting delivery"),
        buildInstance("b", "Paged"),
        buildInstance("c", "Paged", "Awaiting pickup")
      ))
    );
  }

  private static Instance buildInstance(String tenantId, String... itemStatuses) {
    return new Instance()
      .id(INSTANCE_ID)
      .tenantId(tenantId)
      .items(buildItems(itemStatuses));
  }

  private static List<Item> buildItems(String... statuses) {
    return Arrays.stream(statuses)
      .map(ItemBasedTenantPickingStrategyTest::buildItem)
      .toList();
  }

  private static Item buildItem(String status) {
    return new Item()
      .id(UUID.randomUUID().toString())
      .status(new ItemStatus().name(status));
  }

}