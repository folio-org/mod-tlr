package org.folio.domain.entity;

import jakarta.persistence.Convert;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;
import org.folio.domain.converter.UUIDConverter;
import java.util.UUID;

@Entity
@Table(name = "title_level_request")
@Getter
@Setter
@ToString
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class TitleLevelRequestEntity {

  @Id
  @Convert(converter = UUIDConverter.class)
  private UUID id;

}
