"""
OASTH Session Management
========================
Hybrid approach: Uses Playwright once to get session, then caches for HTTP calls.
"""

import json
import os
import time
from pathlib import Path
from typing import Optional, Dict
from dataclasses import dataclass, asdict

# Session cache location
CACHE_DIR = Path.home() / ".cache" / "oasth_widget"
SESSION_FILE = CACHE_DIR / "session.json"

# Session validity (1 hour)
SESSION_EXPIRY_SECONDS = 3600

# Static token discovered through reverse-engineering
STATIC_TOKEN = "e2287129f7a2bbae422f3673c4944d703b84a1cf71e189f869de7da527d01137"


@dataclass
class SessionData:
    """Cached session credentials"""
    phpsessid: str
    token: str
    created_at: float
    
    def is_valid(self) -> bool:
        """Check if session is still valid"""
        return time.time() - self.created_at < SESSION_EXPIRY_SECONDS
    
    def to_dict(self) -> Dict:
        return asdict(self)
    
    @classmethod
    def from_dict(cls, data: Dict) -> 'SessionData':
        return cls(**data)


def _save_session(session: SessionData) -> None:
    """Save session to cache file"""
    CACHE_DIR.mkdir(parents=True, exist_ok=True)
    with open(SESSION_FILE, 'w') as f:
        json.dump(session.to_dict(), f)


def _load_session() -> Optional[SessionData]:
    """Load session from cache file"""
    if not SESSION_FILE.exists():
        return None
    
    try:
        with open(SESSION_FILE, 'r') as f:
            data = json.load(f)
        session = SessionData.from_dict(data)
        
        if session.is_valid():
            return session
        else:
            # Session expired, remove cache
            SESSION_FILE.unlink()
            return None
    except (json.JSONDecodeError, KeyError, TypeError):
        return None


def _create_session_with_browser() -> SessionData:
    """Create new session using headless browser"""
    from playwright.sync_api import sync_playwright
    
    with sync_playwright() as p:
        # Launch minimal browser
        browser = p.firefox.launch(
            headless=True,
            args=["--no-sandbox"]
        )
        
        context = browser.new_context(
            viewport={"width": 800, "height": 600}
        )
        
        page = context.new_page()
        
        # Block unnecessary resources
        page.route("**/*.{png,jpg,jpeg,gif,svg,woff,woff2}", 
                   lambda route: route.abort())
        
        # Load page
        page.goto("https://telematics.oasth.gr/en/", timeout=15000)
        page.wait_for_timeout(2000)
        
        # Extract token from JavaScript
        token = page.evaluate("() => window.token") or STATIC_TOKEN
        
        # Get PHPSESSID from cookies
        cookies = context.cookies()
        phpsessid = next(
            (c['value'] for c in cookies if c['name'] == 'PHPSESSID'),
            None
        )
        
        browser.close()
        
        if not phpsessid:
            raise RuntimeError("Failed to obtain PHPSESSID from browser")
        
        return SessionData(
            phpsessid=phpsessid,
            token=token,
            created_at=time.time()
        )


def get_session(force_refresh: bool = False) -> SessionData:
    """
    Get valid session credentials.
    
    Uses cached session if available and valid,
    otherwise creates new session with browser.
    
    Args:
        force_refresh: Force new session even if cache is valid
        
    Returns:
        SessionData with valid credentials
    """
    if not force_refresh:
        cached = _load_session()
        if cached:
            return cached
    
    # Create new session
    session = _create_session_with_browser()
    _save_session(session)
    
    return session


def clear_session_cache() -> None:
    """Clear cached session"""
    if SESSION_FILE.exists():
        SESSION_FILE.unlink()


if __name__ == "__main__":
    print("Testing session management...")
    session = get_session()
    print(f"PHPSESSID: {session.phpsessid}")
    print(f"Token: {session.token[:20]}...")
    print(f"Valid: {session.is_valid()}")
