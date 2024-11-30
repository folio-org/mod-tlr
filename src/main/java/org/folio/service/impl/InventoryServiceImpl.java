package org.folio.service.impl;

import java.util.Collection;

import org.folio.client.feign.ItemClient;
import org.folio.client.feign.LoanTypeClient;
import org.folio.client.feign.LocationCampusClient;
import org.folio.client.feign.LocationInstitutionClient;
import org.folio.client.feign.LocationLibraryClient;
import org.folio.client.feign.MaterialTypeClient;
import org.folio.domain.dto.Campus;
import org.folio.domain.dto.Campuses;
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

import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;

@Service
@RequiredArgsConstructor
@Log4j2
public class InventoryServiceImpl implements InventoryService {

  private final ItemClient itemClient;
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
    Collection<Item> items = BulkFetcher.fetch(itemClient, query, idIndex, ids, Items::getItems);
    log.info("findItems:: found {} items", items::size);
    return items;
  }

  @Override
  public Collection<Item> findItems(Collection<String> ids) {
    log.info("findItems:: searching items by {} IDs", ids::size);
    log.debug("findItems:: ids={}", ids);
    Collection<Item> items = BulkFetcher.fetch(itemClient, ids, Items::getItems);
    log.info("findItems:: found {} items", items::size);
    return items;
  }

  @Override
  public Collection<MaterialType> findMaterialTypes(Collection<String> ids) {
    log.info("findMaterialTypes:: searching material types by {} IDs", ids::size);
    log.debug("findMaterialTypes:: ids={}", ids);
    Collection<MaterialType> materialTypes = BulkFetcher.fetch(materialTypeClient, ids,
      MaterialTypes::getMtypes);
    log.info("findMaterialTypes:: found {} material types", materialTypes::size);
    return materialTypes;
  }

  @Override
  public Collection<LoanType> findLoanTypes(Collection<String> ids) {
    log.info("findLoanTypes:: searching loan types by {} IDs", ids::size);
    log.debug("findLoanTypes:: ids={}", ids);
    Collection<LoanType> loanTypes = BulkFetcher.fetch(loanTypeClient, ids, LoanTypes::getLoantypes);
    log.info("findLoanTypes:: found {} loan types", loanTypes::size);
    return loanTypes;
  }

  @Override
  public Collection<Library> findLibraries(Collection<String> ids) {
    log.info("findLibraries:: searching libraries by {} IDs", ids::size);
    log.debug("findLibraries:: ids={}", ids);
    Collection<Library> libraries = BulkFetcher.fetch(libraryClient, ids, Libraries::getLoclibs);
    log.info("findLibraries:: found {} libraries", libraries::size);
    return libraries;
  }

  @Override
  public Collection<Campus> findCampuses(Collection<String> ids) {
    log.info("findCampuses:: searching campuses by {} IDs", ids::size);
    log.debug("findCampuses:: ids={}", ids);
    Collection<Campus> campuses = BulkFetcher.fetch(campusClient, ids, Campuses::getLoccamps);
    log.info("findCampuses:: found {} campuses", campuses::size);
    return campuses;
  }

  @Override
  public Collection<Institution> findInstitutions(Collection<String> ids) {
    log.info("findInstitutions:: searching institutions by {} IDs", ids::size);
    log.debug("findInstitutions:: ids={}", ids);
    Collection<Institution> institutions = BulkFetcher.fetch(institutionClient, ids,
      Institutions::getLocinsts);
    log.info("findInstitutions:: found {} institutions", institutions::size);
    return institutions;
  }

}
