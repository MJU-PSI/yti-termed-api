package fi.thl.termed.service.node.specification;

import static fi.thl.termed.util.collect.StreamUtils.zip;

import fi.thl.termed.domain.Graph;
import fi.thl.termed.domain.Node;
import fi.thl.termed.domain.NodeId;
import fi.thl.termed.domain.Type;
import fi.thl.termed.util.query.AndSpecification;
import fi.thl.termed.util.query.OrSpecification;
import fi.thl.termed.util.query.Specification;
import java.util.Arrays;
import java.util.List;
import java.util.stream.IntStream;
import java.util.stream.Stream;

/**
 * Factory methods for node specifications
 */
public final class NodeSpecifications {

  private static NodeSpecificationParser queryParser = new NodeSpecificationParser();

  private NodeSpecifications() {
  }

  public static Specification<NodeId, Node> specifyByQuery(
      List<Graph> graphs, List<Type> types, Type domain, String query) {

    AndSpecification<NodeId, Node> spec = new AndSpecification<>();

    spec.and(new NodesByGraphId(domain.getGraphId()));
    spec.and(new NodesByTypeId(domain.getId()));

    if (!query.isEmpty()) {
      spec.and(new TypeBasedNodeSpecificationFilter(types).apply(domain,
          new NodeGraphAndTypeSpecificationResolver(graphs, types)
              .apply(queryParser.apply(query))));
    }

    return spec;
  }

  public static Specification<NodeId, Node> specifyByAnyPropertyPrefix(Type type, String query) {
    AndSpecification<NodeId, Node> spec = new AndSpecification<>();

    spec.and(new NodesByGraphId(type.getGraphId()));
    spec.and(new NodesByTypeId(type.getId()));

    if (!query.isEmpty()) {
      List<String> prefixes = Arrays.asList(query.split("\\s"));

      // boosts for first few text attributes in the query
      Stream<Integer> boosts = IntStream.iterate(8, b -> b > 2 ? b / 2 : 1).boxed();

      Specification<NodeId, Node> prefixSpec = zip(type.getTextAttributes().stream(), boosts)
          .flatMap(attributeAndBoost -> prefixes.stream()
              .map(prefix -> new NodesByPropertyPrefix(
                  attributeAndBoost.getKey().getId(), prefix, attributeAndBoost.getValue())))
          .collect(OrSpecification::new, OrSpecification::or, OrSpecification::or);

      spec.and(prefixSpec);
    }

    return spec;
  }

}
