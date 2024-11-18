package org.folio.service;

import java.util.Collection;

import org.folio.domain.dto.Campus;
import org.folio.domain.dto.Institution;
import org.folio.domain.dto.Item;
import org.folio.domain.dto.Library;
import org.folio.domain.dto.LoanType;
import org.folio.domain.dto.MaterialType;
import org.folio.support.CqlQuery;

public interface InventoryService {
  Collection<Item> findItems(CqlQuery query, String idIndex, Collection<String> ids);
  Collection<Item> findItems(Collection<String> ids);
  Collection<MaterialType> findMaterialTypes(Collection<String> ids);
  Collection<LoanType> findLoanTypes(Collection<String> ids);
  Collection<Library> findLibraries(Collection<String> ids);
  Collection<Campus> findCampuses(Collection<String> ids);
  Collection<Institution> findInstitutions(Collection<String> ids);
}