package com.example.speechpractice;

import org.junit.Test;

import java.util.HashSet;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;

public class PromptCatalogTest {
    @Test
    public void catalogHasTenDistinctUsablePrompts() {
        assertEquals(10, PromptCatalog.all().size());
        HashSet<String> ids = new HashSet<>();
        for (Prompt prompt : PromptCatalog.all()) {
            assertFalse(prompt.id.trim().isEmpty());
            assertFalse(prompt.word.trim().isEmpty());
            assertFalse(prompt.instruction.trim().isEmpty());
            assertFalse(prompt.illustration == 0);
            assertEquals(true, ids.add(prompt.id));
        }
    }
}
