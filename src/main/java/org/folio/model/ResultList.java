package org.folio.model;

import com.fasterxml.jackson.annotation.JsonAlias;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor(staticName = "of")
public class ResultList<T> {

  /**
   * Page number.
   */
  @JsonAlias("total_records")
  private int totalRecords = 0;

  /**
   * Paged result data.
   */
  private List<T> result = Collections.emptyList();

  /**
   * Creates empty result list.
   *
   * @param <R> generic type for result item.
   * @return empty result list.
   */
  public static <R> ResultList<R> empty() {
    return new ResultList<>();
  }

  /**
   * Creates result list from given resource.
   *
   * @param <R> generic type for result item.
   * @return empty result list.
   */
  public static <R> ResultList<R> asSinglePage(List<R> result) {
    return new ResultList<>(result.size(), result);
  }

  @SafeVarargs
  public static <R> ResultList<R> asSinglePage(R... records) {
    return new ResultList<>(records.length, Arrays.asList(records));
  }
}
