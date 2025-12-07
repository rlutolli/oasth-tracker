package com.oasth.widget

import org.junit.Assert.*
import org.junit.Test

/**
 * Unit tests for version comparison logic
 */
class VersionCheckTest {
    
    private fun isNewerVersion(latest: String, current: String): Boolean {
        val latestParts = latest.split(".").mapNotNull { it.toIntOrNull() }
        val currentParts = current.split(".").mapNotNull { it.toIntOrNull() }
        
        for (i in 0 until maxOf(latestParts.size, currentParts.size)) {
            val l = latestParts.getOrElse(i) { 0 }
            val c = currentParts.getOrElse(i) { 0 }
            if (l > c) return true
            if (l < c) return false
        }
        return false
    }
    
    @Test
    fun `newer major version is detected`() {
        assertTrue(isNewerVersion("3.0.0", "2.1.0"))
    }
    
    @Test
    fun `newer minor version is detected`() {
        assertTrue(isNewerVersion("2.2.0", "2.1.0"))
    }
    
    @Test
    fun `newer patch version is detected`() {
        assertTrue(isNewerVersion("2.1.1", "2.1.0"))
    }
    
    @Test
    fun `same version is not newer`() {
        assertFalse(isNewerVersion("2.1.0", "2.1.0"))
    }
    
    @Test
    fun `older version is not newer`() {
        assertFalse(isNewerVersion("2.0.0", "2.1.0"))
    }
    
    @Test
    fun `handles different version lengths`() {
        assertTrue(isNewerVersion("2.1.0.1", "2.1.0"))
        assertFalse(isNewerVersion("2.1", "2.1.0"))
    }
}
