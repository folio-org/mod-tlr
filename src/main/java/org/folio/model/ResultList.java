package org.folio.model;

import com.fasterxml.jackson.annotation.JsonAlias;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.util.Collections;
import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor(staticName = "of")
public class ResultList<T> {
  @JsonAlias("total_records")
  private int totalRecords = 0;

  private List<T> result = Collections.emptyList();

  public static <R> ResultList<R> of(List<R> result) {
    return new ResultList<>(result.size(), result);
  }
}
