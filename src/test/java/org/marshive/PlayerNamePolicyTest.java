package org.marshive;

import org.junit.Test;
import java.util.List;
import static org.junit.Assert.*;

public class PlayerNamePolicyTest {
    @Test public void acceptsSixChineseOrTwelveAsciiAndMixedNamesAtTheBoundary() {
        for (String name : new String[]{"阳光开心朋友", "Abc123_- xy!", "阳光好友AB12", "中abcdefghij"}) {
            assertEquals(name, PlayerNamePolicy.truncatePlayerName(name));
            assertEquals(name, PlayerNamePolicy.truncateRoomName(name));
            assertEquals("[VS]" + name, PlayerNamePolicy.truncateRoomName("[VS]" + name));
            assertEquals("[COOP]" + name, PlayerNamePolicy.truncateRoomName("[COOP]" + name));
        }
    }

    @Test public void truncatesChineseAsciiAndMixedNamesAndCountsWhitespace() {
        String[][] cases = {
                {"阳光开心好朋友", "阳光开心好朋"}, {"abcdefghijklm", "abcdefghijkl"},
                {"阳光好友AB123", "阳光好友AB12"}, {"阳光开心朋友 ", "阳光开心朋友"},
                {" abcdefghijkl", " abcdefghijk"}, {"中abcdefghijk", "中abcdefghij"},
                {"abcdefghijk中Z", "abcdefghijk"}, {"a".repeat(255), "a".repeat(12)}
        };
        for (String[] sample : cases) {
            assertEquals(sample[1], PlayerNamePolicy.truncatePlayerName(sample[0]));
            assertEquals("[VS]" + sample[1], PlayerNamePolicy.truncateRoomName("[VS]" + sample[0]));
            assertEquals("[COOP]" + sample[1], PlayerNamePolicy.truncateRoomName("[COOP]" + sample[0]));
        }
    }

    @Test public void fullWidthAndSupplementaryCharactersCostTwoUnitsWithoutSplittingSurrogates() {
        for (String point : new String[]{"Ａ", "😊", "𠮷"}) {
            assertEquals(point.repeat(6), PlayerNamePolicy.truncatePlayerName(point.repeat(6)));
            assertEquals(point.repeat(6), PlayerNamePolicy.truncatePlayerName(point.repeat(7)));
        }
        assertEquals("e\u0301".repeat(4), PlayerNamePolicy.truncatePlayerName("e\u0301".repeat(5)));
    }

    @Test public void doesNotLeavePartialCombiningCharactersOrEmojiSequences() {
        assertEquals("abcdefghij", PlayerNamePolicy.truncatePlayerName("abcdefghije\u0301Z"));
        assertEquals("abcd👩🏽‍💻", PlayerNamePolicy.truncatePlayerName("abcd👩🏽‍💻Z"));
        assertEquals("abcde", PlayerNamePolicy.truncatePlayerName("abcde👩🏽‍💻Z"));
        assertEquals("[VS]abcde", PlayerNamePolicy.truncateRoomName("[VS]abcde👩🏽‍💻Z"));
    }

    @Test public void onlyOneExactRoomPrefixIsExemptAndPlayerPrefixesStillCount() {
        assertEquals("[VS]abcdefghijkl", PlayerNamePolicy.truncateRoomName("[VS]abcdefghijklm"));
        assertEquals("[VS]abcdefgh", PlayerNamePolicy.truncatePlayerName("[VS]abcdefghijkl"));
        assertEquals("[COOP]abcdef", PlayerNamePolicy.truncatePlayerName("[COOP]abcdefghijkl"));
        assertEquals("[VS][VS]abcdefgh", PlayerNamePolicy.truncateRoomName("[VS][VS]abcdefghijkl"));
        assertEquals("[COOP][VS]abcdefgh", PlayerNamePolicy.truncateRoomName("[COOP][VS]abcdefghijkl"));
        assertEquals("[vs]abcdefgh", PlayerNamePolicy.truncateRoomName("[vs]abcdefghijkl"));
        assertEquals(" [VS]abcdefg", PlayerNamePolicy.truncateRoomName(" [VS]abcdefghijkl"));
    }

    @Test public void rejectsMalformedCharactersAndTruncatesBeforeMasking() throws Exception {
        for (String name : new String[]{null, "bad\nname", "bad\u0000name", "\uD800", "\uDC00", "a\u2028b",
                "abcdefghijkl\n", "abcdefghijkl\uD800"}) {
            assertThrows(IllegalArgumentException.class, () -> PlayerNamePolicy.truncatePlayerName(name));
            assertThrows(IllegalArgumentException.class, () -> PlayerNamePolicy.truncateRoomName(name));
        }
        RoomManager rooms = new RoomManager(0, new SensitiveWordFilter(List.of("坏")));
        Room room = rooms.createRoom("[VS]" + "坏".repeat(7), null, 3215);
        assertEquals(1, rooms.roomCount());
        assertEquals(1001, room.getId());
        assertEquals("[VS]******", room.getName());
    }
}
