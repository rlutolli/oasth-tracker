package com.oasth.widget.logic

import com.oasth.widget.data.BusArrival

object FilterLogic {

    /**
     * Parses the raw filter string into a map of StreetID -> Set of Allowed Lines.
     * Format: "1306:57,01N; 1234:10"
     */
    fun parseFilters(filterString: String): Map<String, Set<String>> {
        val filterMap = mutableMapOf<String, Set<String>>()
        if (filterString.isBlank()) return filterMap

        val rules = filterString.split(";")
        for (rule in rules) {
            if (rule.contains(":")) {
                val parts = rule.split(":")
                if (parts.size == 2) {
                    val sId = parts[0].trim()
                    // If multiple lines, split by comma
                    val lines = parts[1].split(",").map { it.trim() }.filter { it.isNotEmpty() }.toSet()
                    if (sId.isNotEmpty() && lines.isNotEmpty()) {
                        filterMap[sId] = lines
                    }
                }
            }
        }
        return filterMap
    }

    /**
     * Applies the filter to a list of arrivals for a specific stop.
     * If the stop is not in the filter map, ALL arrivals are returned (allow-all default).
     * If the stop IS in the map, ONLY lines in the set are returned.
     */
    fun filterArrivals(streetId: String, arrivals: List<BusArrival>, filterMap: Map<String, Set<String>>): List<BusArrival> {
        if (!filterMap.containsKey(streetId)) {
            // No rule for this stop -> Show all
            return arrivals
        }

        val allowedLines = filterMap[streetId] ?: emptySet()
        // Rule exists but is empty? (Shouldn't happen with parsing logic, but strict check: if rule exists, strict whitelist)
        
        return arrivals.filter { arrival ->
            // Check lineId (e.g. "01N") or routeCode if lineId missing. 
            // The service used 'displayLine' property.
            val idToCheck = arrival.displayLine
            idToCheck in allowedLines
        }
    }
}
