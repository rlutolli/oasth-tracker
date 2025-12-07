
import requests
import json

STATIC_TOKEN = "e2287129f7a2bbae422f3673c4944d703b84a1cf71e189f869de7da527d01137"

# We need a PHPSESSID. I'll read it from the token.txt file I generated earlier if possible,
# or just ask the script to get one.
# For simplicity, let's just use the `debug_session.py` logic but specific to this test.

from core.session import get_session

print("Getting session...")
session = get_session() # Should use cached valid session
print(f"Session: {session.phpsessid}")

headers = {
    "User-Agent": "Mozilla/5.0 (Linux; Android 10) AppleWebKit/537.36",
    "Accept": "application/json, text/javascript, */*; q=0.01",
    "X-Requested-With": "XMLHttpRequest",
    "X-CSRF-Token": STATIC_TOKEN,
    "Cookie": f"PHPSESSID={session.phpsessid}",
    "Referer": "https://telematics.oasth.gr/en/"
}

def test_stop(code):
    url = f"https://telematics.oasth.gr/api/?act=getStopArrivals&p1={code}"
    print(f"\nTesting p1={code}...")
    try:
        resp = requests.get(url, headers=headers, timeout=10)
        data = resp.json()
        print(f"Status: {resp.status_code}")
        print(f"Data type: {type(data)}")
        if isinstance(data, list):
            print(f"Arrivals count: {len(data)}")
            if len(data) > 0:
                print(f"First arrival: {data[0]}")
        else:
            print(f"Data: {data}")
    except Exception as e:
        print(f"Error: {e}")

test_stop("1306")
test_stop("964")
