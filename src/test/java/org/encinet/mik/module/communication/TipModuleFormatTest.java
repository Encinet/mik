package org.encinet.mik.module.communication;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TipModuleFormatTest {

    @Test
    void detectsFormatsThatMustBeDeleted() {
        assertTrue(TipModule.usesRemovedTipFormat("""
                <aqua>Old first tip</aqua>
                ===
                Old second tip
                """));
        assertTrue(TipModule.usesRemovedTipFormat(
                "<tip topics=\"home\" triggers=\"chat\">Old</tip>"));
        assertTrue(TipModule.usesRemovedTipFormat(
                "<tip topics=\"home\" scenes=\"periodic chat\">Old</tip>"));
    }

    @Test
    void keepsCurrentCustomCatalogsAndIgnoresCommentExamples() {
        assertFalse(TipModule.usesRemovedTipFormat(
                "<tip topics=\"home\">Current</tip>"));
        assertFalse(TipModule.usesRemovedTipFormat("""
                <!-- Removed example: <tip topics="home" triggers="chat">old</tip> -->
                <tip topics="home">Current</tip>
                """));
        assertFalse(TipModule.usesRemovedTipFormat("unrelated malformed text"));
    }
}
