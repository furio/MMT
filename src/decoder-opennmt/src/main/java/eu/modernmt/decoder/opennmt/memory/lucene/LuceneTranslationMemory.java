package eu.modernmt.decoder.opennmt.memory.lucene;

import eu.modernmt.data.Deletion;
import eu.modernmt.data.TranslationUnit;
import eu.modernmt.decoder.opennmt.memory.ScoreEntry;
import eu.modernmt.decoder.opennmt.memory.TranslationMemory;
import eu.modernmt.decoder.opennmt.memory.lucene.rescoring.LevenshteinRescorer;
import eu.modernmt.decoder.opennmt.memory.lucene.rescoring.Rescorer;
import eu.modernmt.lang.LanguageIndex;
import eu.modernmt.lang.LanguagePair;
import eu.modernmt.model.ContextVector;
import eu.modernmt.model.Domain;
import eu.modernmt.model.Sentence;
import eu.modernmt.model.corpus.MultilingualCorpus;
import org.apache.commons.io.FileUtils;
import org.apache.commons.io.IOUtils;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.apache.lucene.document.Document;
import org.apache.lucene.index.*;
import org.apache.lucene.search.*;
import org.apache.lucene.store.Directory;
import org.apache.lucene.store.FSDirectory;
import org.apache.lucene.util.BytesRefBuilder;
import org.apache.lucene.util.NumericUtils;
import org.apache.lucene.util.Version;

import java.io.File;
import java.io.IOException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Created by davide on 23/05/17.
 */
public class LuceneTranslationMemory implements TranslationMemory {

    private final Logger logger = LogManager.getLogger(LuceneTranslationMemory.class);

    private final Directory indexDirectory;
    private final SentenceQueryBuilder queries;
    private final Rescorer rescorer;
    private final IndexWriter indexWriter;
    private final LanguageIndex languages;

    private DirectoryReader _indexReader;
    private IndexSearcher _indexSearcher;
    private final Map<Short, Long> channels;

    private static File forceMkdir(File directory) throws IOException {
        if (!directory.isDirectory())
            FileUtils.forceMkdir(directory);
        return directory;
    }

    public LuceneTranslationMemory(LanguageIndex languages, File indexPath) throws IOException {
        this(languages, indexPath, new LevenshteinRescorer());
    }

    public LuceneTranslationMemory(LanguageIndex languages, Directory directory) throws IOException {
        this(languages, directory, new LevenshteinRescorer());
    }

    public LuceneTranslationMemory(LanguageIndex languages, File indexPath, Rescorer rescorer) throws IOException {
        this(languages, FSDirectory.open(forceMkdir(indexPath)), rescorer);
    }

    public LuceneTranslationMemory(LanguageIndex languages, Directory directory, Rescorer rescorer) throws IOException {
        this.indexDirectory = directory;
        this.queries = new SentenceQueryBuilder();
        this.rescorer = rescorer;
        this.languages = languages;

        // Index writer setup
        IndexWriterConfig indexConfig = new IndexWriterConfig(Version.LUCENE_4_10_4, Analyzers.getTrainAnalyzer());
        indexConfig.setOpenMode(IndexWriterConfig.OpenMode.CREATE_OR_APPEND);
        indexConfig.setSimilarity(new CustomSimilarity());

        this.indexWriter = new IndexWriter(this.indexDirectory, indexConfig);

        // Read channels status
        if (DirectoryReader.indexExists(this.indexDirectory)) {
            IndexReader reader = this.getIndexReader();

            Term term = newLongTerm(DocumentBuilder.DOMAIN_ID_FIELD, 0);
            IndexSearcher searcher = new IndexSearcher(reader);

            Query query = new TermQuery(term);
            TopDocs docs = searcher.search(query, 1);

            if (docs.scoreDocs.length > 0) {
                Document channelsDocument = searcher.doc(docs.scoreDocs[0].doc);
                this.channels = DocumentBuilder.parseChannels(channelsDocument);
            } else {
                this.channels = new HashMap<>();
            }
        } else {
            this.channels = new HashMap<>();
        }
    }

    private static Term newLongTerm(String field, long value) {
        BytesRefBuilder builder = new BytesRefBuilder();
        NumericUtils.longToPrefixCoded(value, 0, builder);

        return new Term(field, builder.toBytesRef());
    }

    public LanguageIndex getLanguageIndex() {
        return languages;
    }

    public synchronized IndexReader getIndexReader() throws IOException {
        if (this._indexReader == null) {
            this._indexReader = DirectoryReader.open(this.indexDirectory);
            this._indexReader.incRef();
            this._indexSearcher = new IndexSearcher(this._indexReader);
        } else {
            DirectoryReader reader = DirectoryReader.openIfChanged(this._indexReader);

            if (reader != null) {
                this._indexReader.close();
                this._indexReader = reader;
                this._indexReader.incRef();

                this._indexSearcher = new IndexSearcher(this._indexReader);
            }
        }

        return this._indexReader;
    }

    public IndexSearcher getIndexSearcher() throws IOException {
        getIndexReader();
        return this._indexSearcher;
    }

    // TranslationMemory

    @Override
    public void add(Map<Domain, MultilingualCorpus> batch) throws IOException {
        boolean success = false;

        try {
            for (Map.Entry<Domain, MultilingualCorpus> entry : batch.entrySet())
                add(entry.getKey().getId(), entry.getValue());

            this.indexWriter.commit();

            success = true;
        } finally {
            if (!success)
                this.indexWriter.rollback();
        }
    }

    @Override
    public void add(Domain domain, MultilingualCorpus corpus) throws IOException {
        boolean success = false;

        try {
            add(domain.getId(), corpus);

            this.indexWriter.commit();

            success = true;
        } finally {
            if (!success)
                this.indexWriter.rollback();
        }
    }

    private void add(long domain, MultilingualCorpus corpus) throws IOException {
        MultilingualCorpus.MultilingualLineReader reader = null;

        try {
            reader = corpus.getContentReader();

            long begin = System.currentTimeMillis();

            MultilingualCorpus.StringPair pair;
            while ((pair = reader.read()) != null) {
                LanguagePair direction = languages.map(pair.language);

                if (direction != null) {
                    Document document = DocumentBuilder.build(direction, domain, pair.source, pair.target);
                    this.indexWriter.addDocument(document);
                }
            }

            double elapsed = System.currentTimeMillis() - begin;
            elapsed = (int) (elapsed / 100);
            elapsed /= 10.;

            logger.info("Domain " + domain + " imported in " + elapsed + "s");
        } finally {
            IOUtils.closeQuietly(reader);
        }
    }

    @Override
    public void add(LanguagePair direction, Domain domain, Sentence sentence, Sentence translation) throws IOException {
        Document document = DocumentBuilder.build(direction, domain.getId(), sentence, translation);
        this.indexWriter.addDocument(document);
        this.indexWriter.commit();
    }

    @Override
    public ScoreEntry[] search(LanguagePair direction, Sentence source, int limit) throws IOException {
        return search(direction, source, null, this.rescorer, limit);
    }

    public ScoreEntry[] search(LanguagePair direction, Sentence source, Rescorer rescorer, int limit) throws IOException {
        return this.search(direction, source, null, rescorer, limit);
    }

    @Override
    public ScoreEntry[] search(LanguagePair direction, Sentence source, ContextVector contextVector, int limit) throws IOException {
        return this.search(direction, source, contextVector, this.rescorer, limit);
    }

    public ScoreEntry[] search(LanguagePair direction, Sentence source, ContextVector contextVector, Rescorer rescorer, int limit) throws IOException {
        Query query = this.queries.build(direction, source);

        IndexSearcher searcher = getIndexSearcher();

        searcher.setSimilarity(new CustomSimilarity());

        int queryLimit = Math.max(10, limit * 2);
        ScoreDoc[] docs = searcher.search(query, queryLimit).scoreDocs;

        ScoreEntry[] entries = new ScoreEntry[docs.length];
        for (int i = 0; i < docs.length; i++) {
            entries[i] = DocumentBuilder.parseEntry(direction, searcher.doc(docs[i].doc));
            entries[i].score = docs[i].score;
        }

        if (rescorer != null)
            rescorer.rescore(source, entries, contextVector);

        if (entries.length > limit) {
            ScoreEntry[] temp = new ScoreEntry[limit];
            System.arraycopy(entries, 0, temp, 0, limit);
            entries = temp;
        }

        return entries;
    }

    // DataListener

    @Override
    public void onDataReceived(List<TranslationUnit> batch) throws IOException {
        boolean success = false;

        HashMap<Short, Long> newChannels = new HashMap<>(this.channels);

        try {
            for (TranslationUnit unit : batch) {
                Long currentPosition = newChannels.get(unit.channel);

                if (currentPosition == null || currentPosition < unit.channelPosition) {
                    newChannels.put(unit.channel, unit.channelPosition);

                    Document document = DocumentBuilder.build(unit);
                    this.indexWriter.addDocument(document);
                }
            }

            Term id = newLongTerm(DocumentBuilder.DOMAIN_ID_FIELD, 0);
            Document channelsDocument = DocumentBuilder.build(newChannels);
            this.indexWriter.updateDocument(id, channelsDocument);

            this.indexWriter.commit();

            this.channels.putAll(newChannels);
            success = true;
        } finally {
            if (!success)
                this.indexWriter.rollback();
        }
    }

    @Override
    public void onDelete(Deletion deletion) throws Exception {
        Long currentPosition = this.channels.get(deletion.channel);

        if (currentPosition == null || currentPosition < deletion.channelPosition) {
            Term deleteId = newLongTerm(DocumentBuilder.DOMAIN_ID_FIELD, deletion.domain);
            this.indexWriter.deleteDocuments(deleteId);

            this.channels.put(deletion.channel, deletion.channelPosition);

            Term channelsId = newLongTerm(DocumentBuilder.DOMAIN_ID_FIELD, 0);
            Document channelsDocument = DocumentBuilder.build(this.channels);
            this.indexWriter.updateDocument(channelsId, channelsDocument);
            this.indexWriter.commit();
        }
    }

    @Override
    public Map<Short, Long> getLatestChannelPositions() {
        return channels;
    }

    // Closeable

    @Override
    public void close() {
        IOUtils.closeQuietly(this._indexReader);
        IOUtils.closeQuietly(this.indexWriter);
        IOUtils.closeQuietly(this.indexDirectory);
    }

}
