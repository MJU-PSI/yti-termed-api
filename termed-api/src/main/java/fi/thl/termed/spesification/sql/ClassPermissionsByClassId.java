package fi.thl.termed.spesification.sql;

import com.google.common.base.Objects;

import fi.thl.termed.domain.ClassId;
import fi.thl.termed.domain.ObjectRolePermission;
import fi.thl.termed.spesification.SqlSpecification;
import fi.thl.termed.spesification.AbstractSpecification;

public class ClassPermissionsByClassId
    extends AbstractSpecification<ObjectRolePermission<ClassId>, Void>
    implements SqlSpecification<ObjectRolePermission<ClassId>, Void> {

  private ClassId classId;

  public ClassPermissionsByClassId(ClassId classId) {
    this.classId = classId;
  }

  @Override
  public boolean accept(ObjectRolePermission<ClassId> objectRolePermission, Void value) {
    return Objects.equal(objectRolePermission.getObjectId(), classId);
  }

  @Override
  public String sqlQueryTemplate() {
    return "class_scheme_id = ? and class_id = ?";
  }

  @Override
  public Object[] sqlQueryParameters() {
    return new Object[]{classId.getSchemeId(), classId.getId()};
  }

}