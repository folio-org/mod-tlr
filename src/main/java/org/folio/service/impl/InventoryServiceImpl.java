package org.folio.service.impl;

import java.util.Collection;
import java.util.Optional;

import org.folio.client.feign.HoldingClient;
import org.folio.client.feign.InstanceClient;
import org.folio.client.feign.ItemClient;
import org.folio.client.feign.LoanTypeClient;
import org.folio.client.feign.LocationCampusClient;
import org.folio.client.feign.LocationInstitutionClient;
import org.folio.client.feign.LocationLibraryClient;
import org.folio.client.feign.MaterialTypeClient;
import org.folio.domain.dto.Campus;
import org.folio.domain.dto.Campuses;
import org.folio.domain.dto.HoldingsRecord;
import org.folio.domain.dto.HoldingsRecords;
import org.folio.domain.dto.Instance;
import org.folio.domain.dto.Instances;
import org.folio.domain.dto.Institution;
import org.folio.domain.dto.Institutions;
import org.folio.domain.dto.Item;
import org.folio.domain.dto.Items;
import org.folio.domain.dto.Libraries;
import org.folio.domain.dto.Library;
import org.folio.domain.dto.LoanType;
import org.folio.domain.dto.LoanTypes;
import org.folio.domain.dto.MaterialType;
import org.folio.domain.dto.MaterialTypes;
import org.folio.service.InventoryService;
import org.folio.support.BulkFetcher;
import org.folio.support.CqlQuery;
import org.springframework.stereotype.Service;

import feign.FeignException;
import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;

@Service
@RequiredArgsConstructor
@Log4j2
public class InventoryServiceImpl implements InventoryService {

  private final ItemClient itemClient;
  private final InstanceClient instanceClient;
  private final HoldingClient holdingClient;
  private final MaterialTypeClient materialTypeClient;
  private final LoanTypeClient loanTypeClient;
  private final LocationLibraryClient libraryClient;
  private final LocationInstitutionClient institutionClient;
  private final LocationCampusClient campusClient;

  @Override
  public Collection<Item> findItems(CqlQuery query, String idIndex, Collection<String> ids) {
    log.info("findItems:: searching items by query and index: query={}, index={}, ids={}",
      query, idIndex, ids.size());
    log.debug("findItems:: ids={}", ids);
    return BulkFetcher.fetch(itemClient, query, idIndex, ids, Items::getItems);
  }

  @Override
  public Collection<HoldingsRecord> findHoldings(CqlQuery query, String idIndex, Collection<String> ids) {
    log.info("findHoldings:: searching holdings by query and index: query={}, index={}, ids={}",
      query, idIndex, ids.size());
    log.debug("findHoldings:: ids={}", ids);
    return BulkFetcher.fetch(holdingClient, query, idIndex, ids, HoldingsRecords::getHoldingsRecords);
  }

  @Override
  public Collection<HoldingsRecord> findHoldings(Collection<String> ids) {
    log.info("findHoldings:: searching holdings by {} IDs", ids::size);
    return BulkFetcher.fetch(holdingClient, ids, HoldingsRecords::getHoldingsRecords);
  }


  @Override
  public Collection<Instance> findInstances(Collection<String> ids) {
    log.info("findInstances:: searching instances by {} IDs", ids::size);
    log.debug("findInstances:: ids={}", ids);
    return BulkFetcher.fetch(instanceClient, ids, Instances::getInstances);
  }

  @Override
  public Optional<Instance> findInstance(String instanceId) {
    log.info("findInstance:: searching instance {}", instanceId);
    try {
      Instance instance = instanceClient.get(instanceId);
      log.info("findInstance:: instance {} found", instanceId);
      return Optional.of(instance);
    } catch (FeignException.NotFound e) {
      log.warn("findInstance:: instance {} not found", instanceId);
      return Optional.empty();
    }
  }

  @Override
  public Collection<MaterialType> findMaterialTypes(Collection<String> ids) {
    log.info("findMaterialTypes:: searching material types by {} IDs", ids::size);
    log.debug("findMaterialTypes:: ids={}", ids);
    return BulkFetcher.fetch(materialTypeClient, ids, MaterialTypes::getMtypes);
  }

  @Override
  public Collection<LoanType> findLoanTypes(Collection<String> ids) {
    log.info("findLoanTypes:: searching loan types by {} IDs", ids::size);
    log.debug("findLoanTypes:: ids={}", ids);
    return BulkFetcher.fetch(loanTypeClient, ids, LoanTypes::getLoantypes);
  }

  @Override
  public Collection<Library> findLibraries(Collection<String> ids) {
    log.info("findLibraries:: searching libraries by {} IDs", ids::size);
    log.debug("findLibraries:: ids={}", ids);
    return BulkFetcher.fetch(libraryClient, ids, Libraries::getLoclibs);
  }

  @Override
  public Collection<Campus> findCampuses(Collection<String> ids) {
    log.info("findCampuses:: searching campuses by {} IDs", ids::size);
    log.debug("findCampuses:: ids={}", ids);
    return BulkFetcher.fetch(campusClient, ids, Campuses::getLoccamps);
  }

  @Override
  public Collection<Institution> findInstitutions(Collection<String> ids) {
    log.info("findInstitutions:: searching institutions by {} IDs", ids::size);
    log.debug("findInstitutions:: ids={}", ids);
    return BulkFetcher.fetch(institutionClient, ids, Institutions::getLocinsts);
  }

}
