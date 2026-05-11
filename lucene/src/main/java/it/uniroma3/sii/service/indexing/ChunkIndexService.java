package it.uniroma3.sii.service.indexing;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import org.apache.lucene.analysis.standard.StandardAnalyzer;
import org.apache.lucene.document.Document;
import org.apache.lucene.document.Field;
import org.apache.lucene.document.FloatDocValuesField;
import org.apache.lucene.document.IntPoint;
import org.apache.lucene.document.StoredField;
import org.apache.lucene.document.StringField;
import org.apache.lucene.document.TextField;
import org.apache.lucene.index.DirectoryReader;
import org.apache.lucene.index.IndexWriter;
import org.apache.lucene.index.IndexWriterConfig;
import org.apache.lucene.index.Term;
import org.apache.lucene.queryparser.classic.QueryParser;
import org.apache.lucene.search.BooleanClause;
import org.apache.lucene.search.BooleanQuery;
import org.apache.lucene.search.BoostQuery;
import org.apache.lucene.search.IndexSearcher;
import org.apache.lucene.search.MatchAllDocsQuery;
import org.apache.lucene.search.Query;
import org.apache.lucene.search.ScoreDoc;
import org.apache.lucene.search.TermInSetQuery;
import org.apache.lucene.search.TermQuery;
import org.apache.lucene.search.TopDocs;
import org.apache.lucene.store.FSDirectory;
import org.apache.lucene.util.BytesRef;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import it.uniroma3.sii.model.DocumentChunk;
import it.uniroma3.sii.service.storage.StoragePathService;

@Service
public class ChunkIndexService {

    private final StoragePathService paths;
    private final StandardAnalyzer analyzer = new StandardAnalyzer();
    private final String chunkIndexDir;

    public ChunkIndexService(StoragePathService paths, @Value("${prism.lucene.chunk-index-dir}") String chunkIndexDir) {
        this.paths = paths;
        this.chunkIndexDir = chunkIndexDir;
    }

    public void indexDocumentChunks(String docId, String docName, String fileType, List<DocumentChunk> chunks) {
        Path indexPath = resolveIndexPath();
        try {
            Files.createDirectories(indexPath);
            try (FSDirectory directory = FSDirectory.open(indexPath);
                 IndexWriter writer = new IndexWriter(directory, new IndexWriterConfig(analyzer))) {
                writer.deleteDocuments(new TermQuery(new Term("docId", docId)));
                for (DocumentChunk chunk : chunks) {
                    writer.addDocument(toLuceneDocument(docName, fileType, chunk));
                }
                writer.commit();
            }
        } catch (IOException e) {
            throw new IllegalStateException("Errore durante l'aggiornamento indice locale per documento " + docId, e);
        }
    }

    public void deleteDocument(String docId) {
        Path indexPath = resolveIndexPath();
        if (!Files.exists(indexPath)) {
            return;
        }
        try (FSDirectory directory = FSDirectory.open(indexPath);
             IndexWriter writer = new IndexWriter(directory, new IndexWriterConfig(analyzer))) {
            writer.deleteDocuments(new TermQuery(new Term("docId", docId)));
            writer.commit();
        } catch (IOException e) {
            throw new IllegalStateException("Impossibile rimuovere il documento dall'indice locale: " + docId, e);
        }
    }

    /**
     * Restituisce i primi {@code perDoc} chunk per ciascun docId, ordinati per paragrafo crescente.
     * Utile per garantire copertura minima di tutti i documenti di un vault in retrieval.
     */
    public List<ChunkHit> firstChunksPerDoc(Set<String> docIds, int perDoc) {
        if (docIds == null || docIds.isEmpty() || perDoc <= 0) {
            return List.of();
        }
        Path indexPath = resolveIndexPath();
        if (!Files.exists(indexPath)) {
            return List.of();
        }
        List<ChunkHit> output = new ArrayList<>();
        try (FSDirectory directory = FSDirectory.open(indexPath);
             DirectoryReader reader = DirectoryReader.open(directory)) {
            IndexSearcher searcher = new IndexSearcher(reader);
            for (String docId : docIds) {
                Query q = new TermQuery(new Term("docId", docId));
                TopDocs hits = searcher.search(q, perDoc);
                for (ScoreDoc scoreDoc : hits.scoreDocs) {
                    Document doc = searcher.storedFields().document(scoreDoc.doc);
                    output.add(new ChunkHit(
                            doc.get("docId"),
                            doc.get("docName"),
                            doc.get("fileType"),
                            doc.get("chunkId"),
                            doc.get("text"),
                            parseInt(doc.get("pageNumber"), 1),
                            parseInt(doc.get("paragraphIndex"), 0),
                            scoreDoc.score));
                }
            }
            return output;
        } catch (IOException e) {
            throw new IllegalStateException("Errore sampling chunk locale", e);
        }
    }

    public List<ChunkHit> search(String queryText, Set<String> docIds, int topK) {
        Path indexPath = resolveIndexPath();
        if (!Files.exists(indexPath)) {
            return List.of();
        }
        try (FSDirectory directory = FSDirectory.open(indexPath);
             DirectoryReader reader = DirectoryReader.open(directory)) {
            IndexSearcher searcher = new IndexSearcher(reader);
            Query textQuery = buildTextQuery(queryText);

            BooleanQuery.Builder builder = new BooleanQuery.Builder();
            builder.add(textQuery, BooleanClause.Occur.MUST);
            if (docIds != null && !docIds.isEmpty()) {
                List<BytesRef> ids = docIds.stream().map(BytesRef::new).toList();
                builder.add(new TermInSetQuery("docId", ids), BooleanClause.Occur.FILTER);
            }
            Query query = builder.build();
            TopDocs topDocs = searcher.search(query, topK);
            List<ChunkHit> hits = new ArrayList<>();
            for (ScoreDoc scoreDoc : topDocs.scoreDocs) {
                Document doc = searcher.storedFields().document(scoreDoc.doc);
                hits.add(new ChunkHit(
                        doc.get("docId"),
                        doc.get("docName"),
                        doc.get("fileType"),
                        doc.get("chunkId"),
                        doc.get("text"),
                        parseInt(doc.get("pageNumber"), 1),
                        parseInt(doc.get("paragraphIndex"), 0),
                        scoreDoc.score));
            }
            return hits;
        } catch (IOException e) {
            throw new IllegalStateException("Errore durante la ricerca nell'indice locale", e);
        }
    }

    private Document toLuceneDocument(String docName, String fileType, DocumentChunk chunk) {
        Document document = new Document();
        document.add(new StringField("docId", chunk.getDocId(), Field.Store.YES));
        document.add(new StringField("chunkId", chunk.getChunkId(), Field.Store.YES));
        document.add(new StringField("docName", docName, Field.Store.YES));
        document.add(new StringField("fileType", fileType, Field.Store.YES));
        document.add(new TextField("text", chunk.getText(), Field.Store.YES));
        document.add(new IntPoint("pageNumber_point", chunk.getPageNumber()));
        document.add(new StoredField("pageNumber", chunk.getPageNumber()));
        document.add(new IntPoint("paragraphIndex_point", chunk.getParagraphIndex()));
        document.add(new StoredField("paragraphIndex", chunk.getParagraphIndex()));
        document.add(new FloatDocValuesField("confidence", 1f));
        return document;
    }

    private Query buildTextQuery(String queryText) {
        if (queryText == null || queryText.isBlank()) {
            return new MatchAllDocsQuery();
        }
        String escaped = QueryParser.escape(queryText.trim());
        try {
            Query parsed = new QueryParser("text", analyzer).parse(escaped);
            return new BoostQuery(parsed, 1.0f);
        } catch (Exception e) {
            throw new IllegalArgumentException("Query non valida per ricerca locale: " + queryText, e);
        }
    }

    private Path resolveIndexPath() {
        return paths.dataRoot().resolve(chunkIndexDir).toAbsolutePath().normalize();
    }

    private int parseInt(String value, int fallback) {
        if (value == null || value.isBlank()) {
            return fallback;
        }
        try {
            return Integer.parseInt(value);
        } catch (NumberFormatException e) {
            return fallback;
        }
    }

    public record ChunkHit(
            String docId,
            String docName,
            String fileType,
            String chunkId,
            String text,
            int page,
            int paragraphIndex,
            float score) {
    }
}
