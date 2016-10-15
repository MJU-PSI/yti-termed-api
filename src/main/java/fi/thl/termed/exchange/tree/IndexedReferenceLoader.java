package fi.thl.termed.exchange.tree;

import com.google.common.base.Function;
import com.google.common.collect.Maps;

import java.util.List;
import java.util.Map;

import fi.thl.termed.domain.ClassId;
import fi.thl.termed.domain.ReferenceAttributeId;
import fi.thl.termed.domain.Resource;
import fi.thl.termed.domain.ResourceId;
import fi.thl.termed.domain.User;
import fi.thl.termed.service.Service;
import fi.thl.termed.spesification.SpecificationQuery;
import fi.thl.termed.spesification.resource.ResourceReferences;

import static com.google.common.base.Functions.forMap;
import static com.google.common.collect.Lists.newArrayList;
import static com.google.common.collect.Lists.transform;
import static fi.thl.termed.spesification.SpecificationQuery.Engine.LUCENE;

/**
 * Load resource references from index.
 */
public class IndexedReferenceLoader implements Function<Resource, List<Resource>> {

  private Service<ResourceId, Resource> resourceService;
  private User user;
  private ReferenceAttributeId attributeId;
  private ClassId rangeId;

  public IndexedReferenceLoader(Service<ResourceId, Resource> resourceService,
                                User user, ReferenceAttributeId attributeId, ClassId rangeId) {
    this.resourceService = resourceService;
    this.user = user;
    this.attributeId = attributeId;
    this.rangeId = rangeId;
  }

  @Override
  public List<Resource> apply(Resource resource) {
    List<Resource> references = resourceService.get(
        new SpecificationQuery<ResourceId, Resource>(
            new ResourceReferences(new ResourceId(resource), attributeId, rangeId), LUCENE), user);
    return order(references, resource.getReferenceIds().get(attributeId.getId()));
  }

  // preserve reference order
  private List<Resource> order(List<Resource> references, Iterable<ResourceId> orderedIds) {
    Map<ResourceId, Resource> referenceIndex = Maps.uniqueIndex(references, new ToResourceId());
    return transform(newArrayList(orderedIds), forMap(referenceIndex));
  }

}