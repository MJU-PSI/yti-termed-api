package fi.thl.termed.service.node.internal;

import static fi.thl.termed.util.index.lucene.LuceneConstants.CACHED_RESULT_FIELD;
import static org.apache.lucene.document.CompressionTools.decompressString;

import com.google.common.collect.Multimap;
import com.google.gson.Gson;
import com.google.gson.JsonSyntaxException;
import com.google.gson.reflect.TypeToken;
import fi.thl.termed.domain.Node;
import fi.thl.termed.domain.NodeId;
import fi.thl.termed.util.index.lucene.LuceneConstants;
import fi.thl.termed.util.index.lucene.LuceneException;
import java.lang.reflect.Type;
import java.util.function.Function;
import java.util.zip.DataFormatException;
import org.apache.lucene.document.Document;

public class DocumentToNode implements Function<Document, Node> {

  private Gson gson;

  private boolean loadReferrers;
  private Type referrersType = new TypeToken<Multimap<String, NodeId>>() {
  }.getType();

  public DocumentToNode(Gson gson) {
    this(gson, true);
  }

  public DocumentToNode(Gson gson, boolean loadReferrers) {
    this.gson = gson;
    this.loadReferrers = loadReferrers;
  }

  @Override
  public Node apply(Document document) {
    Node cachedNode = loadCachedJsonField(document, CACHED_RESULT_FIELD, Node.class);

    if (loadReferrers) {
      cachedNode.setReferrers(loadCachedJsonField(document, "_cached_referrers", referrersType));
    }

    return cachedNode;
  }

  private <T> T loadCachedJsonField(Document document, String field, Type type) {
    try {
      return gson.fromJson(decompressString(document.getBinaryValue(field)), type);
    } catch (DataFormatException e) {
      throw new LuceneException("Failed to decompress cached value for " +
          document.get(LuceneConstants.DOCUMENT_ID), e);
    } catch (JsonSyntaxException e) {
      throw new LuceneException("Failed to parse cached JSON value for " +
          document.get(LuceneConstants.DOCUMENT_ID), e);
    }
  }

}
