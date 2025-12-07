package com.oasth.widget

import com.oasth.widget.data.BusArrival
import com.oasth.widget.data.SessionData
import com.oasth.widget.data.WidgetConfig
import org.junit.Assert.*
import org.junit.Test

/**
 * Unit tests for data models
 */
class ModelsTest {
    
    @Test
    fun `BusArrival should store correct values`() {
        val arrival = BusArrival(
            lineId = "01K",
            lineDescr = "KAMARA",
            routeCode = "01K-1",
            vehicleCode = "1234",
            estimatedMinutes = 5
        )
        
        assertEquals("01K", arrival.lineId)
        assertEquals("KAMARA", arrival.lineDescr)
        assertEquals("01K-1", arrival.routeCode)
        assertEquals("1234", arrival.vehicleCode)
        assertEquals(5, arrival.estimatedMinutes)
    }
    
    @Test
    fun `SessionData isValid returns true for fresh session`() {
        val session = SessionData(
            phpSessionId = "test123",
            token = "token456",
            createdAt = System.currentTimeMillis()
        )
        
        assertTrue("Fresh session should be valid", session.isValid())
    }
    
    @Test
    fun `SessionData isValid returns false for expired session`() {
        val twoHoursAgo = System.currentTimeMillis() - (2 * 60 * 60 * 1000L)
        val session = SessionData(
            phpSessionId = "test123",
            token = "token456",
            createdAt = twoHoursAgo
        )
        
        assertFalse("2-hour old session should be expired", session.isValid())
    }
    
    @Test
    fun `WidgetConfig stores widget configuration`() {
        val config = WidgetConfig(
            widgetId = 42,
            stopCode = "1029",
            stopName = "ESPEROS"
        )
        
        assertEquals(42, config.widgetId)
        assertEquals("1029", config.stopCode)
        assertEquals("ESPEROS", config.stopName)
    }
    
    @Test
    fun `BusArrival default values are correct`() {
        val arrival = BusArrival()
        
        assertEquals("", arrival.lineId)
        assertEquals("", arrival.lineDescr)
        assertEquals("", arrival.routeCode)
        assertEquals("", arrival.vehicleCode)
        assertEquals(0, arrival.estimatedMinutes)
    }
}
