package org.marshive;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.stream.IntStream;
import static org.junit.Assert.*;

public class SensitiveWordFilterTest {
    @Rule public TemporaryFolder temporary = new TemporaryFolder();

    @Test public void masksChineseFullWidthCaseAndOverlappingTerms() throws Exception {
        SensitiveWordFilter filter = new SensitiveWordFilter(List.of("test", "坏词", "abc", "bcde"));
        assertEquals("[VS] **房 ****", filter.mask("[VS] 坏词房 ＴｅＳＴ"));
        assertEquals("*****", filter.mask("abcde"));
        assertEquals("****-****", filter.mask("test-TEST"));
        assertEquals("[COOP] 阳光😀 123", filter.mask("[COOP] 阳光😀 123"));
    }

    @Test public void normalizationMasksWholeTextElementsWithoutDamagingOtherEmoji() throws Exception {
        SensitiveWordFilter filter = new SensitiveWordFilter(List.of("é", "ss", "σ", "ff", "👩🏽‍💻"));
        assertEquals("*房", filter.mask("e\u0301房"));
        assertEquals("*房", filter.mask("ß房"));
        assertEquals("***", filter.mask("Σσς"));
        assertEquals("*房", filter.mask("\uFB03房"));
        assertEquals("👨‍👩‍👧‍👦 *", filter.mask("👨‍👩‍👧‍👦 👩🏽‍💻"));
        String masked = filter.mask("[VS] 👩🏽‍💻 é");
        assertTrue(masked.getBytes(StandardCharsets.UTF_8).length <= "[VS] 👩🏽‍💻 é".getBytes(StandardCharsets.UTF_8).length);
    }

    @Test public void bundledDictionaryIsUsableWithoutExternalFiles() {
        SensitiveWordFilter filter = SensitiveWordFilter.bundled();
        assertTrue(filter.wordCount() > 100);
        assertEquals("[VS] **房间", filter.mask("[VS] Ｑｑ房间"));
        assertEquals("***房", filter.mask("制作组房"));
        assertEquals("[VS]****", filter.mask("[VS]ＤｉＣｋ"));
        assertEquals("**** **** ***** ***", filter.mask("ShIt FUCK PuSsY ass"));
        assertEquals("***** ******", filter.mask("NiGgA NIGGER"));
        assertEquals("******* ************", filter.mask("ASSHOLE motherfucker"));
        assertNotNull(SensitiveWordFilter.class.getResource("/sensitive_words/LICENSE"));
    }

    @Test public void loadsAllUtf8FilesWithBomCommasAndLinesAndDeduplicates() throws Exception {
        Path directory = temporary.newFolder().toPath();
        Files.writeString(directory.resolve("one.txt"), "\uFEFF坏词,TEST\r\n坏词\n");
        Files.writeString(directory.resolve("two.txt"), "test\n第二词");
        Files.writeString(directory.resolve("ignored.md"), "普通");
        SensitiveWordFilter filter = SensitiveWordFilter.fromDirectory(directory);
        assertEquals(3, filter.wordCount());
        assertEquals("** **** *** 普通", filter.mask("坏词 test 第二词 普通"));
    }

    @Test public void rejectsMissingEmptyAndMalformedDictionaries() throws Exception {
        Path directory = temporary.newFolder().toPath();
        assertThrows(IOException.class, () -> SensitiveWordFilter.fromDirectory(directory.resolve("missing")));
        assertThrows(IOException.class, () -> SensitiveWordFilter.fromDirectory(directory));
        Path words = directory.resolve("words.txt");
        Files.writeString(words, "\uFEFF ,\n\r\n");
        assertThrows(IOException.class, () -> SensitiveWordFilter.fromDirectory(directory));
        Files.write(words, new byte[]{(byte) 0xC3, 0x28});
        assertThrows(IOException.class, () -> SensitiveWordFilter.fromDirectory(directory));
    }

    @Test public void boundsFilesWordsAndTrieGrowth() throws Exception {
        Path directory = temporary.newFolder().toPath();
        for (int i = 0; i < 17; i++) Files.writeString(directory.resolve(i + ".txt"), "word");
        assertThrows(IOException.class, () -> SensitiveWordFilter.fromDirectory(directory));
        Path large = temporary.newFolder().toPath();
        Files.write(large.resolve("large.txt"), new byte[1024 * 1024 + 1]);
        assertThrows(IOException.class, () -> SensitiveWordFilter.fromDirectory(large));
        assertThrows(IOException.class, () -> new SensitiveWordFilter(List.of("x".repeat(129))));
        assertThrows(IOException.class, () -> new SensitiveWordFilter(List.of("\uFDFA".repeat(128))));
        assertThrows(IOException.class, () -> new SensitiveWordFilter(
                IntStream.range(0, 20001).mapToObj(i -> "word" + i).toList()));
        assertThrows(IOException.class, () -> new SensitiveWordFilter(
                IntStream.range(0, 2000).mapToObj(i -> i + "-" + "x".repeat(120)).toList()));
    }

    @Test public void manyOverlappingMatchesAndMaximumProtocolLengthStayBounded() throws Exception {
        SensitiveWordFilter filter = new SensitiveWordFilter(IntStream.rangeClosed(1, 128)
                .mapToObj(i -> "a".repeat(i)).toList());
        assertEquals("*".repeat(255), filter.mask("a".repeat(255)));
        assertThrows(IllegalArgumentException.class, () -> filter.mask("a".repeat(256)));
        assertThrows(IllegalArgumentException.class, () -> filter.mask("花".repeat(86)));
        assertThrows(IllegalArgumentException.class, () -> filter.mask("房\n间"));
        assertThrows(IllegalArgumentException.class, () -> filter.mask("\uD800"));
    }
}
