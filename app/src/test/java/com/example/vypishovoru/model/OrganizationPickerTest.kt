package com.example.vypishovoru.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class OrganizationPickerTest {

    @Test
    fun `1 prazdny seznam vrati null`() {
        assertNull(pickFirstNonBlank(emptyList()))
    }

    @Test
    fun `2 pouze prazdne a whitespace hodnoty vrati null`() {
        assertNull(pickFirstNonBlank(listOf("", "   ", null)))
    }

    @Test
    fun `3 prazdna hodnota se preskoci a vrati prvni platnou`() {
        assertEquals("ACME", pickFirstNonBlank(listOf("", "ACME")))
    }

    @Test
    fun `4 whitespace se preskoci deterministicky prvni platna vitezi`() {
        assertEquals("ACME", pickFirstNonBlank(listOf("  ", "ACME", "XYZ")))
    }

    @Test
    fun `5 prvni platna hodnota vitezi i pri vice zaznamech`() {
        assertEquals("ACME", pickFirstNonBlank(listOf("ACME", "XYZ")))
    }

    @Test
    fun `6 null mezi hodnotami nepadne a preskoci se`() {
        assertEquals("ACME", pickFirstNonBlank(listOf(null, "  ", "ACME")))
    }
}
