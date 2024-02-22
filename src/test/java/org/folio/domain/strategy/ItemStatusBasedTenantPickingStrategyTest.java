package org.folio.domain.strategy;

import static java.util.Collections.emptySet;
import static java.util.Collections.singletonList;
import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
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
class ItemStatusBasedTenantPickingStrategyTest {
  private static final String INSTANCE_ID = UUID.randomUUID().toString();

  @Mock
  private SearchClient searchClient;
  @InjectMocks
  private ItemStatusBasedTenantPickingStrategy strategy;

  @ParameterizedTest
  @MethodSource("parametersForPickTenant")
  void pickTenant(Set<String> expectedTenantIds, Instance instance) {
    Mockito.when(searchClient.searchInstance(Mockito.any()))
      .thenReturn(new SearchInstancesResponse().instances(singletonList(instance)));
    assertEquals(expectedTenantIds, strategy.pickTenants(INSTANCE_ID));
  }

  private static Stream<Arguments> parametersForPickTenant() {
    return Stream.of(
      Arguments.of(emptySet(), null),

      // instances without items are ignored
      Arguments.of(emptySet(), buildInstance()),

      // items without tenantId are ignored
      Arguments.of(emptySet(), buildInstance(buildItem(null, "Available"))),
      Arguments.of(Set.of("a"), buildInstance(
        buildItem(null, "Available"),
        buildItem("a", "Paged")
      )),

      // 1 tenant, 1 item
      Arguments.of(Set.of("a"), buildInstance(buildItem("a", "Available"))),
      Arguments.of(Set.of("a"), buildInstance(buildItem("a", "Checked out"))),
      Arguments.of(Set.of("a"), buildInstance(buildItem("a", "In transit"))),
      Arguments.of(Set.of("a"), buildInstance(buildItem("a", "Paged"))),

      // multiple tenants, same item status, tenant with most items wins
      Arguments.of(Set.of("b"), buildInstance(
        buildItem("a", "Available"),
        buildItem("b", "Available"),
        buildItem("b", "Available"),
        buildItem("b", "Available"),
        buildItem("c", "Available"),
        buildItem("c", "Available")
      )),
      Arguments.of(Set.of("b"), buildInstance(
        buildItem("a", "Checked out"),
        buildItem("b", "Checked out"),
        buildItem("b", "Checked out"),
        buildItem("b", "Checked out"),
        buildItem("c", "Checked out"),
        buildItem("c", "Checked out")
      )),
      Arguments.of(Set.of("b"), buildInstance(
        buildItem("a", "In transit"),
        buildItem("b", "In transit"),
        buildItem("b", "In transit"),
        buildItem("b", "In transit"),
        buildItem("c", "In transit"),
        buildItem("c", "In transit")
      )),
      Arguments.of(Set.of("b"), buildInstance(
        buildItem("a", "Paged"),
        buildItem("b", "Paged"),
        buildItem("b", "Paged"),
        buildItem("b", "Paged"),
        buildItem("c", "Paged"),
        buildItem("c", "Paged")
      )),

      // item priority test: "Available" > ("Checked out" + "In transit") > all others
      Arguments.of(new LinkedHashSet<>(List.of("b", "c", "a")), buildInstance(
        buildItem("a", "Paged"),
        buildItem("a", "Awaiting pickup"),
        buildItem("a", "Awaiting delivery"),
        buildItem("b", "Available"),
        buildItem("c", "Checked out"),
        buildItem("c", "In transit")
      )),
      Arguments.of(new LinkedHashSet<>(List.of("c", "a")), buildInstance(
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
      Arguments.of(Set.of("a"), buildInstance(
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
      .id(INSTANCE_ID)
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
