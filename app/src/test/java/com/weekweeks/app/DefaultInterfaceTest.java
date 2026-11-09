package com.weekweeks.app;

import org.junit.Test;

import static org.junit.Assert.assertTrue;

/** Prevent the regular build from silently falling back to the legacy interface. */
public class DefaultInterfaceTest {
    @Test
    public void regularBuildUsesCurrentInterface() {
        assertTrue("Week weeks must use the current interface by default", BuildConfig.PASTEL_UI);
    }
}
