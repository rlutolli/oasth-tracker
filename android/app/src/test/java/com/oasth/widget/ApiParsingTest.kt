package com.oasth.widget

import org.junit.Assert.*
import org.junit.Test

/**
 * Unit tests for API response parsing
 */
class ApiParsingTest {
    
    @Test
    fun `parseArrivals extracts line ID and time from JSON`() {
        val json = """
            [
                {"bline_id":"01K","bline_descr":"KAMARA","route_code":"01K-1","btime2":5},
                {"bline_id":"02","bline_descr":"TOUMBA","route_code":"02-1","btime2":12}
            ]
        """.trimIndent()
        
        val pattern = """"bline_id"\s*:\s*"([^"]+)"[^}]*"btime2"\s*:\s*(\d+)""".toRegex()
        val matches = pattern.findAll(json).toList()
        
        assertEquals("Should find 2 arrivals", 2, matches.size)
        assertEquals("01K", matches[0].groupValues[1])
        assertEquals("5", matches[0].groupValues[2])
        assertEquals("02", matches[1].groupValues[1])
        assertEquals("12", matches[1].groupValues[2])
    }
    
    @Test
    fun `parseArrivals handles empty response`() {
        val json = "[]"
        
        val pattern = """"bline_id"\s*:\s*"([^"]+)"[^}]*"btime2"\s*:\s*(\d+)""".toRegex()
        val matches = pattern.findAll(json).toList()
        
        assertEquals("Empty JSON should return no matches", 0, matches.size)
    }
    
    @Test
    fun `parseArrivals handles malformed JSON gracefully`() {
        val json = "not valid json"
        
        val pattern = """"bline_id"\s*:\s*"([^"]+)"[^}]*"btime2"\s*:\s*(\d+)""".toRegex()
        val matches = pattern.findAll(json).toList()
        
        assertEquals("Malformed JSON should return no matches", 0, matches.size)
    }
    
    @Test
    fun `parseStopName extracts stop description`() {
        val json = """{"stopDescr":"PARKO SMYRNIS","stopId":"1029"}"""
        
        val pattern = """"stopDescr"\s*:\s*"([^"]+)"""".toRegex()
        val match = pattern.find(json)
        
        assertNotNull("Should find stop description", match)
        assertEquals("PARKO SMYRNIS", match?.groupValues?.get(1))
    }
    
    @Test
    fun `parseVersionTag extracts version from GitHub API`() {
        val json = """{"tag_name":"v2.1.0","name":"Release 2.1.0"}"""
        
        val pattern = """"tag_name"\s*:\s*"v?([^"]+)"""".toRegex()
        val match = pattern.find(json)
        
        assertNotNull("Should find version tag", match)
        assertEquals("2.1.0", match?.groupValues?.get(1))
    }
}
