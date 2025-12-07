package com.oasth.widget.data

import com.google.gson.annotations.SerializedName

/**
 * Bus arrival data from OASTH API
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
    
    @SerializedName("btime2")
    val estimatedMinutes: Int = 0
)

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
    val stopName: String
)
