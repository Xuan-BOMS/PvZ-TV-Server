package org.marshive;

import org.junit.Test;

import static org.junit.Assert.assertEquals;

public class SeedTypeNamesTest {
    @Test
    public void loadsLocalizedNamesFromBundledGameStrings() {
        assertEquals("豌豆射手", SeedTypeNames.zhNameOf(0));
        assertEquals("交通协管僵尸", SeedTypeNames.zhNameOf(102));
        assertEquals("红针花", SeedTypeNames.zhNameOf(210));
    }
}
