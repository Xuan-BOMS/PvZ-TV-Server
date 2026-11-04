package org.marshive;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.text.Normalizer;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

/** Literal Unicode matching, following PvZ Ultimate's public room-name masking policy. */
final class SensitiveWordFilter {
    private static final int MAX_FILES = 16;
    private static final int MAX_FILE_BYTES = 1024 * 1024;
    private static final int MAX_TOTAL_BYTES = 4 * MAX_FILE_BYTES;
    private static final int MAX_WORDS = 20000;
    private static final int MAX_WORD_LENGTH = 128;
    private static final int MAX_NODES = 200000;
    private static final int MAX_NORMALIZED_LENGTH = 8192;
    private static final Pattern TEXT_ELEMENT = Pattern.compile("\\X");
    private static final List<String> BUNDLED_FILES = List.of(
            "广告.txt", "色情类.txt", "涉枪涉爆违法信息关键词.txt", "政治类.txt", "作者类.txt", "英文.txt");

    private final Node root = new Node();
    private final int maximumWordLength;
    private final int wordCount;

    SensitiveWordFilter(Collection<String> words) throws IOException {
        if (words.size() > MAX_WORDS) throw new IOException("Too many sensitive words");
        int nodes = 1;
        int longest = 0;
        Set<String> normalizedWords = new HashSet<>();
        for (String word : words) {
            if (word == null || word.length() > MAX_WORD_LENGTH) throw new IOException("Sensitive word is too long");
            String normalized = normalize(word.strip());
            if (normalized.isEmpty() || !normalizedWords.add(normalized)) continue;
            int[] points = normalized.codePoints().toArray();
            if (points.length > MAX_WORD_LENGTH) throw new IOException("Normalized sensitive word is too long");
            longest = Math.max(longest, points.length);
            Node node = root;
            for (int point : points) {
                Node child = node.children.get(point);
                if (child == null) {
                    if (++nodes > MAX_NODES) throw new IOException("Sensitive-word trie is too large");
                    child = new Node();
                    node.children.put(point, child);
                }
                node = child;
            }
            node.terminal = true;
        }
        if (normalizedWords.isEmpty()) throw new IOException("Sensitive-word list is empty");
        maximumWordLength = longest;
        wordCount = normalizedWords.size();
    }

    static SensitiveWordFilter bundled() {
        return Bundled.INSTANCE;
    }

    private static final class Bundled {
        static final SensitiveWordFilter INSTANCE = load();
        private static SensitiveWordFilter load() {
            Set<String> words = new HashSet<>();
            try {
                int totalBytes = 0;
                for (String file : BUNDLED_FILES) {
                    try (InputStream input = SensitiveWordFilter.class.getResourceAsStream("/sensitive_words/" + file)) {
                        if (input == null) throw new IOException("Missing bundled sensitive-word file: " + file);
                        totalBytes += readWords(input, words);
                        if (totalBytes > MAX_TOTAL_BYTES) throw new IOException("Sensitive-word files exceed total size limit");
                    }
                }
                return new SensitiveWordFilter(words);
            } catch (IOException e) {
                throw new UncheckedIOException("Could not load bundled sensitive words", e);
            }
        }
    }

    static SensitiveWordFilter fromDirectory(Path directory) throws IOException {
        List<Path> files;
        try (Stream<Path> entries = Files.list(directory)) {
            files = entries.filter(p -> p.getFileName().toString().endsWith(".txt"))
                    .limit(MAX_FILES + 1L).sorted().toList();
        }
        if (files.isEmpty() || files.size() > MAX_FILES) throw new IOException("Sensitive-word directory must contain 1..16 .txt files");
        Set<String> words = new HashSet<>();
        int totalBytes = 0;
        for (Path file : files) {
            if (!Files.isRegularFile(file)) throw new IOException("Not a sensitive-word file: " + file);
            try (InputStream input = Files.newInputStream(file)) {
                totalBytes += readWords(input, words);
                if (totalBytes > MAX_TOTAL_BYTES) throw new IOException("Sensitive-word files exceed total size limit");
            }
        }
        return new SensitiveWordFilter(words);
    }

    private static int readWords(InputStream input, Set<String> words) throws IOException {
        byte[] bytes = input.readNBytes(MAX_FILE_BYTES + 1);
        if (bytes.length > MAX_FILE_BYTES) throw new IOException("Sensitive-word file exceeds 1 MiB");
        // Report malformed UTF-8 instead of silently running with a damaged dictionary.
        String content = StandardCharsets.UTF_8.newDecoder().decode(ByteBuffer.wrap(bytes)).toString();
        if (content.startsWith("\uFEFF")) content = content.substring(1);
        int start = 0;
        for (int end = 0; end <= content.length(); end++) {
            if (end < content.length() && content.charAt(end) != ',' && content.charAt(end) != '\r' && content.charAt(end) != '\n') continue;
            String word = content.substring(start, end).strip();
            if (word.length() > MAX_WORD_LENGTH) throw new IOException("Sensitive word exceeds 128 UTF-16 characters");
            if (!word.isEmpty()) {
                words.add(word);
                if (words.size() > MAX_WORDS) throw new IOException("Too many sensitive words");
            }
            start = end + 1;
        }
        return bytes.length;
    }

    int wordCount() { return wordCount; }

    String mask(String value) {
        // CREATE already uses a one-byte UTF-8 length; keep that protocol limit unchanged.
        if (value == null || value.length() > 255 || value.getBytes(StandardCharsets.UTF_8).length > 255) {
            throw new IllegalArgumentException("Invalid room name length");
        }
        for (int point : value.codePoints().toArray()) {
            int type = Character.getType(point);
            if (Character.isISOControl(point) || type == Character.SURROGATE
                    || type == Character.LINE_SEPARATOR || type == Character.PARAGRAPH_SEPARATOR) {
                throw new IllegalArgumentException("Invalid room name character");
            }
        }
        List<String> elements = new ArrayList<>();
        int[] normalized = new int[MAX_NORMALIZED_LENGTH];
        int[] sources = new int[MAX_NORMALIZED_LENGTH];
        int length = 0;
        Matcher matcher = TEXT_ELEMENT.matcher(value);
        while (matcher.find()) {
            String element = matcher.group();
            int index = elements.size();
            elements.add(element);
            int[] points = normalize(element).codePoints().toArray();
            if (length + points.length > MAX_NORMALIZED_LENGTH) throw new IllegalArgumentException("Room name normalization limit exceeded");
            for (int point : points) {
                normalized[length] = point;
                sources[length++] = index;
            }
        }
        int[] coverage = new int[elements.size() + 1];
        boolean changed = false;
        for (int start = 0; start < length; start++) {
            Node node = root;
            for (int end = start; end < Math.min(length, start + maximumWordLength); end++) {
                node = node.children.get(normalized[end]);
                if (node == null) break;
                if (node.terminal) {
                    coverage[sources[start]]++;
                    coverage[sources[end] + 1]--;
                    changed = true;
                }
            }
        }
        if (!changed) return value;
        StringBuilder result = new StringBuilder(value.length());
        int active = 0;
        for (int i = 0; i < elements.size(); i++) {
            active += coverage[i];
            result.append(active > 0 ? "*" : elements.get(i));
        }
        return result.toString();
    }

    private static String normalize(String value) {
        return Normalizer.normalize(value, Normalizer.Form.NFKC).toLowerCase(Locale.ROOT)
                .replace("ß", "ss").replace('ς', 'σ');
    }

    private static final class Node {
        final Map<Integer, Node> children = new HashMap<>();
        boolean terminal;
    }
}
