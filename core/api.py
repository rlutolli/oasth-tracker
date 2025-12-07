"""
OASTH API Client
================
Pure HTTP API client using cached session credentials.
"""

import requests
from typing import List, Optional
from .session import get_session, SessionData
from .models import BusArrival, BusLine, BusStop


BASE_URL = "https://telematics.oasth.gr/api/"


class OasthAPI:
    """OASTH API client with automatic session management"""
    
    def __init__(self, session_data: Optional[SessionData] = None):
        """
        Initialize API client.
        
        Args:
            session_data: Optional pre-loaded session. If None, will load automatically.
        """
        self._session_data = session_data
        self._http = requests.Session()
    
    def _ensure_session(self) -> SessionData:
        """Ensure we have valid session credentials"""
        if self._session_data is None or not self._session_data.is_valid():
            self._session_data = get_session()
        return self._session_data
    
    def _get_headers(self) -> dict:
        """Get headers for API requests"""
        session = self._ensure_session()
        return {
            'User-Agent': 'Mozilla/5.0 (X11; Linux x86_64) AppleWebKit/537.36',
            'Accept': 'application/json, text/javascript, */*; q=0.01',
            'X-Requested-With': 'XMLHttpRequest',
            'X-CSRF-Token': session.token,
            'Origin': 'https://telematics.oasth.gr',
            'Referer': 'https://telematics.oasth.gr/en/',
        }
    
    def _get_cookies(self) -> dict:
        """Get cookies for API requests"""
        session = self._ensure_session()
        return {'PHPSESSID': session.phpsessid}
    
    def _request(self, act: str, params: dict = None, method: str = 'GET') -> dict:
        """Make API request"""
        url = f"{BASE_URL}?act={act}"
        
        if params:
            param_str = "&".join(f"{k}={v}" for k, v in params.items())
            url = f"{url}&{param_str}"
        
        headers = self._get_headers()
        cookies = self._get_cookies()
        
        if method == 'GET':
            resp = self._http.get(url, headers=headers, cookies=cookies, timeout=10)
        else:
            resp = self._http.post(url, headers=headers, cookies=cookies, timeout=10)
        
        if resp.status_code == 401:
            # Session expired, refresh and retry
            self._session_data = get_session(force_refresh=True)
            headers = self._get_headers()
            cookies = self._get_cookies()
            
            if method == 'GET':
                resp = self._http.get(url, headers=headers, cookies=cookies, timeout=10)
            else:
                resp = self._http.post(url, headers=headers, cookies=cookies, timeout=10)
        
        resp.raise_for_status()
        return resp.json()
    
    def get_arrivals(self, stop_code: str) -> List[BusArrival]:
        """
        Get bus arrivals for a stop.
        
        Args:
            stop_code: The stop code (e.g., "3344")
            
        Returns:
            List of upcoming bus arrivals
        """
        data = self._request('getStopArrivals', {'p1': stop_code})
        
        if not isinstance(data, list):
            return []
        
        return [BusArrival.from_api(item) for item in data]
    
    def get_lines(self) -> List[BusLine]:
        """Get all bus lines"""
        data = self._request('webGetLines', method='POST')
        
        if not isinstance(data, list):
            return []
        
        return [BusLine.from_api(item) for item in data]
    
    def get_lines_detailed(self) -> List[dict]:
        """Get all bus lines with ML info"""
        return self._request('webGetLinesWithMLInfo', method='POST')


# Convenience function
def get_arrivals(stop_code: str) -> List[BusArrival]:
    """
    Quick function to get arrivals for a stop.
    
    Args:
        stop_code: The stop code
        
    Returns:
        List of bus arrivals
    """
    api = OasthAPI()
    return api.get_arrivals(stop_code)


if __name__ == "__main__":
    print("Testing OASTH API client...")
    
    api = OasthAPI()
    
    # Test getting lines
    print("\n📊 Getting bus lines...")
    lines = api.get_lines()
    print(f"Found {len(lines)} lines")
    if lines:
        print(f"Sample: {lines[0].line_id} - {lines[0].line_descr[:50]}")
    
    # Test getting arrivals
    print("\n🚌 Getting arrivals for stop 100...")
    arrivals = api.get_arrivals("100")
    if arrivals:
        for a in arrivals[:3]:
            print(f"  Line {a.line_id}: {a.estimated_minutes} min")
    else:
        print("  No arrivals (may be valid - stop might have no buses now)")
