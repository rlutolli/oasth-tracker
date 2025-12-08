package com.oasth.widget.data

import com.google.gson.annotations.SerializedName

/**
 * Bus arrival data from OASTH API
 * API response: {"btime2":"5","route_code":"39","veh_code":"2740"}
 */
data class BusArrival(
    @SerializedName("bline_id")
    val lineId: String = "",
    
    @SerializedName("bline_descr")
    val lineDescr: String = "",
    
    @SerializedName("route_code")
    val routeCode: String = "",
    
    @SerializedName("veh_code")
    val vehicleCode: String = "",
    
    // btime2 comes as String from API, need custom handling
    @SerializedName("btime2")
    val rawTime: String = "0"
) {
    /** Estimated minutes as Int, parsed from rawTime string */
    val estimatedMinutes: Int
        get() = rawTime.toIntOrNull() ?: 0
    
    /** Display name - prefer lineId if available, otherwise routeCode */
    val displayLine: String
        get() = lineId.ifEmpty { routeCode }
}

/**
 * Bus line information
 */
data class BusLine(
    @SerializedName("LineCode")
    val lineCode: String = "",
    
    @SerializedName("LineID")
    val lineId: String = "",
    
    @SerializedName("LineDescr")
    val lineDescr: String = ""
)

/**
 * Session credentials for API access
 */
data class SessionData(
    val phpSessionId: String,
    val token: String,
    val createdAt: Long
) {
    fun isValid(): Boolean {
        val oneHourMs = 60 * 60 * 1000L
        return System.currentTimeMillis() - createdAt < oneHourMs
    }
}

/**
 * Widget configuration stored per widget instance
 */
data class WidgetConfig(
    val widgetId: Int,
    val stopCode: String,
    val stopName: String,
    val lineFilter: String = ""  // Comma-separated line IDs to show (empty = show all)
) {
    /**
     * Parse line filter into a set of allowed lines.
     * Returns null if no filter (show all lines).
     */
    fun getAllowedLines(): Set<String>? {
        if (lineFilter.isBlank()) return null
        return lineFilter.split(",")
            .map { it.trim().uppercase() }
            .filter { it.isNotEmpty() }
            .toSet()
    }
}
