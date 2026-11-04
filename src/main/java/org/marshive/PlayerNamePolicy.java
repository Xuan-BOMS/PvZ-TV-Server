package org.marshive;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Shared length policy for room owners, guests and spectators. */
final class PlayerNamePolicy {
    private static final int MAX_UNITS = 12;
    private static final Pattern TEXT_ELEMENT = Pattern.compile("\\X");

    private PlayerNamePolicy() {}

    static String truncatePlayerName(String name) {
        return truncate(name, 0);
    }

    static String truncateRoomName(String name) {
        // Only the first protocol mode prefix is exempt, never a prefix in a guest name.
        int offset = name != null && name.startsWith("[VS]") ? 4
                : name != null && name.startsWith("[COOP]") ? 6 : 0;
        return truncate(name, offset);
    }

    private static String truncate(String name, int offset) {
        if (name == null) throw new IllegalArgumentException("Missing player name");
        // Validate the whole input, including any suffix that will be removed.
        for (int i = offset; i < name.length();) {
            int point = name.codePointAt(i);
            int type = Character.getType(point);
            if (Character.isISOControl(point) || type == Character.SURROGATE
                    || type == Character.LINE_SEPARATOR || type == Character.PARAGRAPH_SEPARATOR) {
                throw new IllegalArgumentException("Invalid player name character");
            }
            i += Character.charCount(point);
        }
        int units = 0;
        Matcher elements = TEXT_ELEMENT.matcher(name).region(offset, name.length());
        while (elements.find()) {
            for (int i = elements.start(); i < elements.end();) {
                int point = name.codePointAt(i);
                // Keep the same budget, but cut only at complete text-element boundaries.
                units += point <= 0x7F ? 1 : 2;
                if (units > MAX_UNITS) return name.substring(0, elements.start());
                i += Character.charCount(point);
            }
        }
        return name;
    }
}
