package fi.thl.termed.spesification.sql;

import com.google.common.base.MoreObjects;
import com.google.common.base.Objects;

import java.util.UUID;

import fi.thl.termed.domain.Class;
import fi.thl.termed.domain.ClassId;
import fi.thl.termed.spesification.SqlSpecification;
import fi.thl.termed.spesification.AbstractSpecification;

public class ClassesBySchemeId extends AbstractSpecification<ClassId, Class>
    implements SqlSpecification<ClassId, Class> {

  private UUID schemeId;

  public ClassesBySchemeId(UUID schemeId) {
    this.schemeId = schemeId;
  }

  @Override
  public boolean accept(ClassId classId, Class cls) {
    return Objects.equal(classId.getSchemeId(), schemeId);
  }

  @Override
  public String sqlQueryTemplate() {
    return "scheme_id = ?";
  }

  @Override
  public Object[] sqlQueryParameters() {
    return new Object[]{schemeId};
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) {
      return true;
    }
    if (o == null || getClass() != o.getClass()) {
      return false;
    }
    ClassesBySchemeId that = (ClassesBySchemeId) o;
    return Objects.equal(schemeId, that.schemeId);
  }

  @Override
  public int hashCode() {
    return Objects.hashCode(schemeId);
  }

  @Override
  public String toString() {
    return MoreObjects.toStringHelper(this)
        .add("schemeId", schemeId)
        .toString();
  }

}