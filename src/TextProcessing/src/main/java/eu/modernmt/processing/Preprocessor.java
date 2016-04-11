package eu.modernmt.processing;

import eu.modernmt.model.Sentence;
import eu.modernmt.processing.framework.*;
import eu.modernmt.processing.numbers.NumericWordFactory;
import eu.modernmt.processing.tokenizer.SimpleTokenizer;
import eu.modernmt.processing.tokenizer.Tokenizer;
import eu.modernmt.processing.tokenizer.Tokenizers;
import eu.modernmt.processing.util.RareCharsNormalizer;
import eu.modernmt.processing.util.WhitespacesNormalizer;
import eu.modernmt.processing.xmessage.XMessageParser;
import eu.modernmt.processing.xmessage.XMessageTokenizer;
import eu.modernmt.processing.xmessage.XMessageWordFactory;
import eu.modernmt.processing.xml.XMLStringBuilder;
import org.apache.commons.io.IOUtils;

import java.io.Closeable;
import java.io.IOException;
import java.util.List;
import java.util.Locale;

/**
 * Created by davide on 19/02/16.
 */
public class Preprocessor implements Closeable {

    private static final XMessageParser XMessageParser;
    private static final XMLStringBuilder XMLStringBuilder;
    private static final RareCharsNormalizer RareCharsNormalizer;
    private static final WhitespacesNormalizer WhitespacesNormalizer;
    private static final XMessageTokenizer XMessageTokenizer;
    private static final SentenceBuilder SentenceBuilder;

    static {
        XMessageParser = new XMessageParser();
        XMLStringBuilder = new XMLStringBuilder();
        RareCharsNormalizer = new RareCharsNormalizer();
        WhitespacesNormalizer = new WhitespacesNormalizer();
        XMessageTokenizer = new XMessageTokenizer();

        SentenceBuilder = new SentenceBuilder();
        SentenceBuilder.addWordFactory(NumericWordFactory.class);
        SentenceBuilder.addWordFactory(XMessageWordFactory.class);
    }

    private final ProcessingPipeline<String, Sentence> pipelineWithTokenization;
    private final ProcessingPipeline<String, Sentence> pipelineWithoutTokenization;

    public static ProcessingPipeline<String, Sentence> getPipeline(Locale language, boolean tokenize) {
        return getPipeline(language, tokenize, Runtime.getRuntime().availableProcessors());
    }

    public static ProcessingPipeline<String, Sentence> getPipeline(Locale language, boolean tokenize, int threads) {
        Tokenizer languageTokenizer = tokenize ? Tokenizers.forLanguage(language) : new SimpleTokenizer();

        return new ProcessingPipeline.Builder<String, String>()
                .setThreads(threads)
                // Pre EditableString
                .add(XMessageParser)
                .add(XMLStringBuilder)

                // String normalization
                .add(RareCharsNormalizer)
                .add(WhitespacesNormalizer)

                // Tokenization
                .add(languageTokenizer)
                .add(XMessageTokenizer)

                // Sentence building
                .add(SentenceBuilder)
                .create();
    }

    public Preprocessor(Locale language) {
        this(language, Runtime.getRuntime().availableProcessors());
    }

    public Preprocessor(Locale language, int threads) {
        pipelineWithTokenization = getPipeline(language, true, threads);
        pipelineWithoutTokenization = getPipeline(language, false, threads);
    }

    public Sentence[] process(List<String> text, boolean tokenize) throws ProcessingException {
        return process(text.toArray(new String[text.size()]), tokenize);
    }

    public Sentence[] process(String[] text, boolean tokenize) throws ProcessingException {
        BatchTask task = new BatchTask(text);
        ProcessingPipeline<String, Sentence> pipeline = tokenize ? pipelineWithTokenization : pipelineWithoutTokenization;

        try {
            ProcessingJob<String, Sentence> job = pipeline.createJob(task, task);
            job.start();
            job.join();
        } catch (InterruptedException e) {
            throw new RuntimeException("Unexpected exception", e);
        }

        return task.getResult();
    }

    public Sentence process(String text, boolean tokenize) throws ProcessingException {
        if (tokenize)
            return pipelineWithTokenization.process(text);
        else
            return pipelineWithoutTokenization.process(text);
    }

    @Override
    public void close() {
        IOUtils.closeQuietly(pipelineWithTokenization);
        IOUtils.closeQuietly(pipelineWithoutTokenization);
    }

    private static class BatchTask implements PipelineInputStream<String>, PipelineOutputStream<Sentence> {

        private String[] source;
        private Sentence[] result;
        private int readIndex;
        private int writeIndex;

        public BatchTask(String[] source) {
            this.source = source;
            this.result = new Sentence[source.length];
            this.readIndex = 0;
            this.writeIndex = 0;
        }

        @Override
        public String read() {
            if (readIndex < source.length)
                return source[readIndex++];
            else
                return null;
        }

        @Override
        public void write(Sentence value) {
            result[writeIndex++] = value;
        }

        public Sentence[] getResult() {
            return result;
        }

        @Override
        public void close() throws IOException {
        }

    }

    public static void main(String[] args) throws Throwable {
        ProcessingPipeline<String, Sentence> pipeline = null;

        try {
            pipeline = Preprocessor.getPipeline(Locale.ENGLISH, true);
            System.out.println(pipeline.process("Hello <b>world</b>"));
        } finally {
            IOUtils.closeQuietly(pipeline);
        }
    }
}
