"""
OASTH Data Models
=================
Data structures for bus arrivals, stops, and lines.
"""

from dataclasses import dataclass
from typing import List, Optional


@dataclass
class BusArrival:
    """A bus arrival at a stop"""
    line_id: str           # e.g., "01", "31"
    line_descr: str        # Line description
    route_code: str        # Internal route code
    vehicle_code: str      # Bus vehicle code
    estimated_minutes: int # Minutes until arrival
    
    @classmethod
    def from_api(cls, data: dict) -> 'BusArrival':
        """Create from API response"""
        return cls(
            line_id=data.get('bline_id', data.get('line_id', '')),
            line_descr=data.get('bline_descr', data.get('line_descr', '')),
            route_code=data.get('route_code', ''),
            vehicle_code=data.get('veh_code', ''),
            estimated_minutes=int(data.get('btime2', data.get('estimated_time', 0)))
        )


@dataclass
class BusStop:
    """A bus stop"""
    stop_code: str        # e.g., "3344"
    stop_descr: str       # Stop name
    stop_lat: float       # Latitude
    stop_lng: float       # Longitude
    
    @classmethod
    def from_api(cls, data: dict) -> 'BusStop':
        """Create from API response"""
        return cls(
            stop_code=data.get('StopCode', data.get('stop_code', '')),
            stop_descr=data.get('StopDescr', data.get('stop_descr', '')),
            stop_lat=float(data.get('StopLat', data.get('stop_lat', 0))),
            stop_lng=float(data.get('StopLng', data.get('stop_lng', 0)))
        )


@dataclass
class BusLine:
    """A bus line"""
    line_code: str        # Internal code
    line_id: str          # Public ID (e.g., "01", "31")
    line_descr: str       # Description
    
    @classmethod
    def from_api(cls, data: dict) -> 'BusLine':
        """Create from API response"""
        return cls(
            line_code=data.get('LineCode', data.get('line_code', '')),
            line_id=data.get('LineID', data.get('line_id', '')).strip(),
            line_descr=data.get('LineDescr', data.get('line_descr', ''))
        )
