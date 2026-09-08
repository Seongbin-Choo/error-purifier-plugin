package com.errorpurifier.action;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PurifyErrorLogActionTest {

    @Test
    void sendsOnlySelectedConsoleTextWhenSelectionExists() {
        PurifyErrorLogAction.AnalysisInput input = PurifyErrorLogAction.selectAnalysisInput(
                "first line\nsecret outside selection\nselected failure",
                "selected failure"
        );

        assertEquals("selected failure", input.rawLog());
        assertEquals("selected failure", input.selectedText());
    }

    @Test
    void sendsFullConsoleWhenSelectionDoesNotExist() {
        PurifyErrorLogAction.AnalysisInput input = PurifyErrorLogAction.selectAnalysisInput(
                "full console log",
                null
        );

        assertEquals("full console log", input.rawLog());
        assertNull(input.selectedText());
    }

    @Test
    void treatsBlankSelectionAsNoSelection() {
        PurifyErrorLogAction.AnalysisInput input = PurifyErrorLogAction.selectAnalysisInput(
                "full console log",
                "   "
        );

        assertEquals("full console log", input.rawLog());
        assertNull(input.selectedText());
    }

    @Test
    void acceptsLogAtOneMillionCharacterBoundary() {
        assertFalse(PurifyErrorLogAction.exceedsLogPayloadLimit("x".repeat(1_000_000)));
    }

    @Test
    void rejectsLogAboveOneMillionCharacterBoundary() {
        assertTrue(PurifyErrorLogAction.exceedsLogPayloadLimit("x".repeat(1_000_001)));
    }
}
