package eu.modernmt.context.lucene;

import eu.modernmt.context.ContextAnalyzer;
import eu.modernmt.context.ContextAnalyzerException;
import eu.modernmt.context.ContextScore;
import eu.modernmt.model.Domain;
import eu.modernmt.model.corpus.Corpus;
import eu.modernmt.model.corpus.impl.StringCorpus;
import eu.modernmt.model.corpus.impl.parallel.FileCorpus;
import org.apache.lucene.document.Document;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Created by davide on 09/05/16.
 */
public class LuceneAnalyzer implements ContextAnalyzer {

    private ContextAnalyzerIndex index;

    public LuceneAnalyzer(File indexPath, Locale language) throws IOException {
        this.index = new ContextAnalyzerIndex(indexPath, language);
    }

    @Override
    public void add(Domain domain, Corpus corpus) throws ContextAnalyzerException {
        this.index.add(DocumentBuilder.createDocument(domain, corpus));
    }

    @Override
    public void add(Map<Domain, Corpus> corpora) throws ContextAnalyzerException {
        ArrayList<Document> documents = new ArrayList<>(corpora.size());

        for (Map.Entry<Domain, Corpus> entries : corpora.entrySet()) {
            documents.add(DocumentBuilder.createDocument(entries.getKey(), entries.getValue()));
        }

        this.index.add(documents);
    }

    @Override
    public List<ContextScore> getContext(String query, int limit) throws ContextAnalyzerException {
        return getContext(new StringCorpus(null, null, query), limit);
    }

    @Override
    public List<ContextScore> getContext(File source, int limit) throws ContextAnalyzerException {
        return getContext(new FileCorpus(source, null, null), limit);
    }

    @Override
    public List<ContextScore> getContext(Corpus query, int limit) throws ContextAnalyzerException {
        return this.index.getSimilarDocuments(query, limit);
    }

    @Override
    public void close() throws IOException {
        this.index.close();
    }

}