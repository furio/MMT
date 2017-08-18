import multiprocessing
import os
import re
from HTMLParser import HTMLParser

from cli import mmt_javamain
from cli.libs import fileutils, shell
from cli.mmt import BilingualCorpus

__author__ = 'Davide Caroselli'


def _TrainingPreprocessor_filter_file(corpus, dest_folder, langs, min_thr, max_thr, ratio_thr):
    dest_corpus = BilingualCorpus.make_parallel(corpus.name, dest_folder, corpus.langs)

    regex = re.compile('\s+')

    def _tok(line):
        return filter(lambda x: x, regex.split(line))

    with corpus.reader(langs) as reader:
        with dest_corpus.writer(langs) as writer:
            for source, target in reader:
                src_length = len(_tok(source))
                trg_length = len(_tok(target))

                if src_length > max_thr or trg_length > max_thr:
                    continue
                if src_length < min_thr or trg_length < min_thr:
                    continue

                ratio = float(max(src_length, trg_length)) / float(min(src_length, trg_length))
                if ratio > ratio_thr:
                    continue

                writer.writelines(source, target)


class Tokenizer:
    def __init__(self, lang, print_tags=False, print_placeholders=True):
        self._lang = lang
        self._print_tags = print_tags
        self._print_placeholders = print_placeholders
        self._java_mainclass = 'eu.modernmt.cli.PreprocessorMain'

    def process_corpora(self, corpora, dest_folder):
        fileutils.makedirs(dest_folder, exist_ok=True)

        for corpus in corpora:
            for lang in corpus.langs:
                source = corpus.get_file(lang)
                dest = BilingualCorpus.make_parallel(corpus.name, dest_folder, [lang])

                self.process_file(source, dest, lang)

        return BilingualCorpus.list(dest_folder)

    def process_file(self, source, dest, lang):
        args = ['--lang', self._lang]
        if not self._print_tags:
            args.append('--no-tags')
        if self._print_placeholders:
            args.append('--print-placeholders')

        command = mmt_javamain(self._java_mainclass, args=args)

        with open(source) as input_stream:
            with open(dest.get_file(lang), 'w') as output_stream:
                shell.execute(command, stdin=input_stream, stdout=output_stream)


class TMCleaner:
    def __init__(self, source_lang, target_lang):
        self._source_lang = source_lang
        self._target_lang = target_lang

        self._java_mainclass = 'eu.modernmt.cli.CleaningPipelineMain'

    def clean(self, corpora, output_path, log=None):
        if log is None:
            log = shell.DEVNULL

        # read memory size
        mem_bytes = os.sysconf('SC_PAGE_SIZE') * os.sysconf('SC_PHYS_PAGES')  # e.g. 4015976448
        mem_mb = mem_bytes / (1024. ** 2)  # e.g. 3.74

        extended_heap_mb = int(mem_mb * 90 / 100)

        args = ['-s', self._source_lang, '-t', self._target_lang, '--output', output_path, '--input']

        input_paths = set([corpus.get_folder() for corpus in corpora])

        for root in input_paths:
            args.append(root)

        command = mmt_javamain(self._java_mainclass, args=args, max_heap_mb=extended_heap_mb)
        shell.execute(command, stdout=log, stderr=log)

        return BilingualCorpus.list(output_path)


class TrainingPreprocessor:
    DEV_FOLDER_NAME = 'dev'
    TEST_FOLDER_NAME = 'test'

    def __init__(self, source_lang, target_lang, clean_ratio=3, clean_min=1, clean_max=80):
        self._source_lang = source_lang
        self._target_lang = target_lang

        self._ratio = clean_ratio
        self._min = clean_min
        self._max = clean_max

        self._java_mainclass = 'eu.modernmt.cli.TrainingPipelineMain'

    def process(self, corpora, output_path, data_path=None, log=None, vb_path=None):
        if log is None:
            log = shell.DEVNULL

        args = ['-s', self._source_lang, '-t', self._target_lang, '--output', output_path]
        if vb_path:
            args.append('-v')
            args.append(vb_path)

        args.append('--input')
        input_paths = set([corpus.get_folder() for corpus in corpora])

        for root in input_paths:
            args.append(root)

        if data_path is not None:
            args.append('--dev')
            args.append(os.path.join(data_path, TrainingPreprocessor.DEV_FOLDER_NAME))
            args.append('--test')
            args.append(os.path.join(data_path, TrainingPreprocessor.TEST_FOLDER_NAME))

        command = mmt_javamain(self._java_mainclass, args)
        shell.execute(command, stdout=log, stderr=log)

        return BilingualCorpus.splitlist(self._source_lang, self._target_lang, roots=output_path)

    def filter(self, corpora, dest_folder):
        if not os.path.isdir(dest_folder):
            fileutils.makedirs(dest_folder, exist_ok=True)

        langs = (self._source_lang, self._target_lang)

        workers = min(multiprocessing.cpu_count(), len(corpora))
        pool = multiprocessing.Pool(workers)

        try:
            jobs = [(corpus, dest_folder, langs, self._min, self._max, self._ratio) for corpus in corpora]
            aync_jobs = [pool.apply_async(_TrainingPreprocessor_filter_file, job) for job in jobs]
            for job in aync_jobs:
                job.get()
        finally:
            pool.terminate()

        return BilingualCorpus.list(dest_folder)


class XMLEncoder:
    __TAG_NAME = '([a-zA-Z]|_|:)([a-zA-Z]|[0-9]|\\.|-|_|:|)*'
    __TAG_REGEX = re.compile('(<(' + __TAG_NAME + ')[^>]*/?>)|(<!(' + __TAG_NAME + ')[^>]*[^/]>)|(</(' +
                             __TAG_NAME + ')[^>]*>)|(<!--)|(-->)')
    __HTML = HTMLParser()

    def __init__(self):
        pass

    def encode(self, corpora, dest_folder):
        if not os.path.isdir(dest_folder):
            fileutils.makedirs(dest_folder, exist_ok=True)

        for corpus in corpora:
            for lang in corpus.langs:
                source = corpus.get_file(lang)
                dest_file = BilingualCorpus.make_parallel(corpus.name, dest_folder, [lang]).get_file(lang)

                self.encode_file(source, dest_file, delete_nl=True)

        return BilingualCorpus.list(dest_folder)

    def encode_file(self, source, dest_file, delete_nl=False):
        with open(dest_file, 'wb') as outstream:
            with open(source) as instream:
                for line in instream:
                    encoded = self.encode_string(line.decode('utf-8'))
                    encoded = encoded.rstrip('\r\n')

                    if delete_nl:
                        encoded = encoded.replace('\n', ' ').replace('\r', '')

                    encoded += '\n'
                    outstream.write(encoded.encode('utf-8'))

    @staticmethod
    def __escape(string):
        escaped = XMLEncoder.__HTML.unescape(string)
        return escaped \
            .replace('&', '&amp;') \
            .replace('"', '&quot;') \
            .replace('\'', '&apos;') \
            .replace('<', '&lt;') \
            .replace('>', '&gt;')

    def encode_string(self, string):
        result = []
        index = 0

        for match in XMLEncoder.__TAG_REGEX.finditer(string):
            start = match.start()
            end = match.end()

            result.append(self.__escape(string[index:start]))
            result.append(match.group())

            index = end

        if index < len(string):
            result.append(self.__escape(string[index:]))

        return ''.join(result)

    def decode_string(self, string):
        result = []
        index = 0

        for match in self.__TAG_REGEX.finditer(string):
            start = match.start()
            end = match.end()

            if index != start:
                result.append(self.__HTML.unescape(string[index:start]).strip())

            index = end

        if index < len(string):
            result.append(self.__HTML.unescape(string[index:]).strip())

        return ' '.join(result)