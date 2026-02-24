
import requests
import json

URL = "https://telematics.oasth.gr/api/?act=getStopArrivals&p1=1307"

# Use the FRESH session I captured
cookies = {
    'PHPSESSID': 'h2daist5tpv86h10aotc6lpch4'
}

headers = {
    'X-Requested-With': 'XMLHttpRequest',
    'User-Agent': 'Mozilla/5.0 (Linux; Android 10; K) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/114.0.0.0 Mobile Safari/537.36'
}

print(f"Checking Stop 1307 with session {cookies['PHPSESSID']}...")

try:
    # TRY POST first (as app does)
    r = requests.post(URL, cookies=cookies, headers=headers)
    print(f"POST Status: {r.status_code}")
    print(f"POST Body First 200 chars: {r.text[:200]}")
    
    if r.status_code == 200:
        try:
            data = r.json()
            if isinstance(data, list):
                print(f"✅ Post returned {len(data)} arrivals")
                for item in data:
                    print(f"   Line {item.get('route_code')} - {item.get('btime2')} mins")
            else:
                print(f"⚠️ Post returned non-list: {type(data)}")
        except json.JSONDecodeError:
            print("❌ POST response is NOT JSON")
            
except Exception as e:
    print(f"Error: {e}")
