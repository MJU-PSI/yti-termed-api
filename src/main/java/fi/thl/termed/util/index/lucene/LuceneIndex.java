package fi.thl.termed.util.index.lucene;

import static com.google.common.base.Strings.isNullOrEmpty;
import static fi.thl.termed.util.collect.FunctionUtils.toUnchecked;
import static fi.thl.termed.util.collect.StreamUtils.findFirstAndClose;
import static fi.thl.termed.util.collect.StreamUtils.toStreamWithTimeout;
import static fi.thl.termed.util.index.lucene.LuceneConstants.DOCUMENT_ID;
import static java.util.Collections.emptyList;
import static java.util.Collections.singleton;
import static java.util.Objects.requireNonNull;
import static org.apache.lucene.index.IndexWriterConfig.OpenMode.CREATE_OR_APPEND;

import fi.thl.termed.util.Converter;
import fi.thl.termed.util.collect.ListUtils;
import fi.thl.termed.util.collect.StreamUtils;
import fi.thl.termed.util.collect.Tuple;
import fi.thl.termed.util.concurrent.ExecutorUtils;
import fi.thl.termed.util.index.Index;
import fi.thl.termed.util.query.LuceneSortField;
import fi.thl.termed.util.query.LuceneSpecification;
import fi.thl.termed.util.query.Specification;
import java.io.IOException;
import java.io.Serializable;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.stream.Stream;

import org.apache.commons.lang3.StringUtils;
import org.apache.lucene.analysis.Analyzer;
import org.apache.lucene.document.Document;
import org.apache.lucene.document.Field;
import org.apache.lucene.document.StringField;
import org.apache.lucene.index.IndexWriter;
import org.apache.lucene.index.IndexWriterConfig;
import org.apache.lucene.index.Term;
import org.apache.lucene.search.BooleanQuery;
import org.apache.lucene.search.IndexSearcher;
import org.apache.lucene.search.MatchAllDocsQuery;
import org.apache.lucene.search.Query;
import org.apache.lucene.search.SearcherFactory;
import org.apache.lucene.search.SearcherManager;
import org.apache.lucene.search.Sort;
import org.apache.lucene.search.SortField;
import org.apache.lucene.search.TermQuery;
import org.apache.lucene.search.TopFieldDocs;
import org.apache.lucene.search.TotalHitCountCollector;
import org.apache.lucene.store.Directory;
import org.apache.lucene.store.FSDirectory;
import org.apache.lucene.store.RAMDirectory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class LuceneIndex<K extends Serializable, V> implements Index<K, V> {

  private final Logger log = LoggerFactory.getLogger(getClass());

  private Converter<V, Document> documentConverter;
  private Converter<K, String> keyConverter;

  private IndexWriter writer;
  private SearcherManager searcherManager;

  private ExecutorService indexingExecutor;
  private ScheduledExecutorService scheduledExecutorService;

  public LuceneIndex(String directoryPath,
      Converter<K, String> keyConverter,
      Converter<V, Document> documentConverter) {

    this.keyConverter = keyConverter;
    this.documentConverter = documentConverter;

    try {
      Analyzer a = new LowerCaseWhitespaceAnalyzer();
      IndexWriterConfig c = new IndexWriterConfig(a)
          .setOpenMode(CREATE_OR_APPEND)
          .setCodec(new TermedCodec());
      this.writer = new IndexWriter(openDirectory(directoryPath), c);
      this.searcherManager = new SearcherManager(writer, new SearcherFactory());
    } catch (IOException e) {
      throw new LuceneException(e);
    }

    this.indexingExecutor = ExecutorUtils.newScheduledThreadPool(1);
    this.scheduledExecutorService = ExecutorUtils.newScheduledThreadPool(5);

    this.scheduledExecutorService.scheduleAtFixedRate(this::refresh, 0, 1, TimeUnit.SECONDS);
    this.scheduledExecutorService.scheduleAtFixedRate(this::commit, 0, 10, TimeUnit.SECONDS);

    BooleanQuery.setMaxClauseCount(Integer.MAX_VALUE);
  }

  private Directory openDirectory(String directoryPath) throws IOException {
    log.info("Opening index directory {}", directoryPath);
    return isNullOrEmpty(directoryPath) ? new RAMDirectory()
        : FSDirectory.open(Paths.get(directoryPath));
  }

  @Override
  public void index(K key, V value) {
    Term documentIdTerm = new Term(DOCUMENT_ID, keyConverter.apply(key));

    Document document = requireNonNull(documentConverter.apply(value));
    document.add(new StringField(documentIdTerm.field(), documentIdTerm.text(), Field.Store.YES));

    try {
      writer.updateDocument(documentIdTerm, document);
    } catch (IOException e) {
      throw new LuceneException(e);
    }
  }

  @Override
  public Stream<V> get(Specification<K, V> specification,
      List<fi.thl.termed.util.query.Sort> sort, int max) {
    return get(specification, sort, max, null, documentConverter.inverse());
  }

  /**
   * Expert method for searching and loading results with custom Lucene Document deserializer.
   */
  public Stream<V> get(Specification<K, V> specification, List<fi.thl.termed.util.query.Sort> sort,
      int max, Set<String> fieldsToLoad, Function<Document, V> documentDeserializer) {
    IndexSearcher searcher = null;
    try {
      searcher = tryAcquire();
      Query query = ((LuceneSpecification<K, V>) specification).luceneQuery();
      return query(searcher, query, max, sort, fieldsToLoad, documentDeserializer);
    } catch (IOException e) {
      tryRelease(searcher);
      throw new LuceneException(e);
    }
  }

  @Override
  public Stream<K> getKeys(Specification<K, V> specification,
      List<fi.thl.termed.util.query.Sort> sort, int max) {
    IndexSearcher searcher = null;
    try {
      searcher = tryAcquire();
      Query query = ((LuceneSpecification<K, V>) specification).luceneQuery();
      return query(searcher, query, max, sort, singleton(DOCUMENT_ID),
          d -> keyConverter.applyInverse(d.get(DOCUMENT_ID)));
    } catch (IOException e) {
      tryRelease(searcher);
      throw new LuceneException(e);
    }
  }

  @Override
  public long count(Specification<K, V> specification) {
    IndexSearcher searcher = null;
    try {
      searcher = tryAcquire();
      TotalHitCountCollector hitCountCollector = new TotalHitCountCollector();
      Query query = ((LuceneSpecification<K, V>) specification).luceneQuery();
      searcher.search(query, hitCountCollector);
      return hitCountCollector.getTotalHits();
    } catch (IOException e) {
      throw new LuceneException(e);
    } finally {
      tryRelease(searcher);
    }
  }

  @Override
  public boolean isEmpty() {
    IndexSearcher searcher = null;
    try {
      searcher = tryAcquire();
      TotalHitCountCollector hitCountCollector = new TotalHitCountCollector();
      searcher.search(new MatchAllDocsQuery(), hitCountCollector);
      return hitCountCollector.getTotalHits() == 0;
    } catch (IOException e) {
      throw new LuceneException(e);
    } finally {
      tryRelease(searcher);
    }
  }

  @Override
  public Optional<V> get(K id) {
    IndexSearcher searcher = null;
    try {
      TermQuery q = new TermQuery(new Term(DOCUMENT_ID, keyConverter.apply(id)));
      searcher = tryAcquire();
      return findFirstAndClose(query(searcher, q, 1, emptyList(), documentConverter.inverse()));
    } catch (IOException e) {
      tryRelease(searcher);
      throw new LuceneException(e);
    }
  }

  private <E> Stream<E> query(IndexSearcher searcher, Query query, int max,
      List<fi.thl.termed.util.query.Sort> orderBy, Function<Document, E> documentDeserializer)
      throws IOException {
    return query(searcher, query, max, orderBy, null, documentDeserializer);
  }

  // null in fieldsToLoad means load all
  private <E> Stream<E> query(IndexSearcher searcher, Query query, int max,
      List<fi.thl.termed.util.query.Sort> sort, Set<String> fieldsToLoad,
      Function<Document, E> documentDeserializer) throws IOException {

    long start = System.currentTimeMillis();

    Stream<Integer> docs;

    if (ListUtils.isNullOrEmpty(sort) && (max < 0 || max == Integer.MAX_VALUE)) {
      SimpleAllCollector c = new SimpleAllCollector();
      searcher.search(query, c);
      docs = c.getDocs().stream();
    } else {
      TopFieldDocs topDocs = searcher.search(query, max > 0 ? max : Integer.MAX_VALUE, sort(sort));
      docs = Arrays.stream(topDocs.scoreDocs).map(sd -> sd.doc);
    }

    return toStreamWithTimeout(docs
            .map(toUnchecked(doc -> searcher.doc(doc, fieldsToLoad)))
            .map(documentDeserializer)
            .onClose(() -> tryRelease(searcher))
            .onClose(() -> {
              if (log.isTraceEnabled()) {
                log.trace("{} in {} ms", StringUtils.normalizeSpace(query.toString()), System.currentTimeMillis() - start);
              }
            }),
        scheduledExecutorService, 1, TimeUnit.HOURS, query::toString);
  }

  private IndexSearcher tryAcquire() {
    try {
      return searcherManager.acquire();
    } catch (IOException e) {
      throw new LuceneException(e);
    }
  }

  private void tryRelease(IndexSearcher searcher) {
    try {
      searcherManager.release(searcher);
    } catch (IOException e) {
      throw new LuceneException(e);
    }
  }

  private Sort sort(List<fi.thl.termed.util.query.Sort> sort) {
    return new Sort(ListUtils.nullToEmpty(sort).stream()
        .filter(s -> s instanceof LuceneSortField)
        .map(s -> (LuceneSortField) s)
        .map(LuceneSortField::toLuceneSortField)
        .toArray(SortField[]::new));
  }

  @Override
  public void delete(K key) {
    try {
      writer.deleteDocuments(new Term(DOCUMENT_ID, keyConverter.apply(key)));
    } catch (IOException e) {
      throw new LuceneException(e);
    }
  }

  public void refresh() {
    try {
      searcherManager.maybeRefresh();
    } catch (IOException e) {
      throw new LuceneException(e);
    }
  }

  public void refreshBlocking() {
    try {
      searcherManager.maybeRefreshBlocking();
    } catch (IOException e) {
      throw new LuceneException(e);
    }
  }

  public void commit() {
    try {
      writer.commit();
    } catch (IOException e) {
      throw new LuceneException(e);
    }
  }

  public void close() {
    log.debug("Closing {}", getClass().getSimpleName());

    try {
      indexingExecutor.shutdown();
      scheduledExecutorService.shutdown();
      searcherManager.close();
      writer.close();
    } catch (IOException e) {
      throw new LuceneException(e);
    }
  }

  private class IndexingTask implements Callable<Void> {

    private Supplier<Stream<K>> keyStreamProvider;
    private Function<K, Optional<V>> valueProvider;

    IndexingTask(Supplier<Stream<K>> keyStreamProvider, Function<K, Optional<V>> valueProvider) {
      this.keyStreamProvider = keyStreamProvider;
      this.valueProvider = valueProvider;
    }

    @Override
    public Void call() {
      log.info("Indexing");

      try (Stream<K> keyStream = keyStreamProvider.get()) {
        StreamUtils.zipIndex(keyStream, Tuple::of).forEach(t -> {

          try {
            Optional<V> value = valueProvider.apply(t._1);

            if (value.isPresent()) {
              index(t._1, value.get());
            } else {
              delete(t._1);
            }
          } catch (Throwable ex) {
            log.error("", ex);
            throw ex;
          }

          if (t._2 % 1000 == 0) {
            log.debug("Indexed {} values", t._2);
          }
        });
      }

      log.info("Done");
      return null;
    }

  }

}
