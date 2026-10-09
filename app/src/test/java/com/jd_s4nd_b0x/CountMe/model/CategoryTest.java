package com.jd_s4nd_b0x.CountMe.model;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class CategoryTest {

    @Test
    public void holdsNameAndSelection() {
        Category c = new Category("Lab", false);
        assertEquals("Lab", c.getName());
        assertFalse(c.isSelected());
        c.setSelected(true);
        c.setName("Theory");
        assertTrue(c.isSelected());
        assertEquals("Theory", c.getName());
    }
}
