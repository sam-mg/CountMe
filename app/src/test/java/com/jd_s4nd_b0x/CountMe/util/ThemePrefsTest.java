package com.jd_s4nd_b0x.CountMe.util;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import android.content.Context;

import androidx.test.core.app.ApplicationProvider;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;

@RunWith(RobolectricTestRunner.class)
public class ThemePrefsTest {

    @Test
    public void choiceIsRememberedOnDeviceAndCanBeToggledBack() {
        Context context = ApplicationProvider.getApplicationContext();
        ThemePrefs.setDark(context, true);
        assertTrue(ThemePrefs.isDark(context));
        ThemePrefs.apply(context);
        assertTrue(ThemePrefs.isDark(context));
        ThemePrefs.setDark(context, false);
        assertFalse(ThemePrefs.isDark(context));
    }
}
