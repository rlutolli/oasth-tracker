
import requests
import json

# Use the verified working session
cookies = {'PHPSESSID': 'h2daist5tpv86h10aotc6lpch4'}

# Headers from user's log
headers = {
    'X-Requested-With': 'XMLHttpRequest',
    'User-Agent': 'Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/142.0.0.0 Safari/537.36',
    'Origin': 'https://telematics.oasth.gr',
    'Referer': 'https://telematics.oasth.gr/',
    'Content-Type': 'application/x-www-form-urlencoded',
    'X-CSRF-Token': 'e2287129f7a2bbae422f3673c4944d703b84a1cf71e189f869de7da527d01137'
}

def test_resolve(external_code):
    print(f"\n🔍 Resolving External Code: {external_code}")
    
    # 1. Try getStopBySIP (SIP often means External ID)
    # p1 = external code
    url = "https://telematics.oasth.gr/api/?act=getStopBySIP&p1=" + external_code
    
    try:
        # POST is required
        r = requests.post(url, cookies=cookies, headers=headers)
        print(f"  Endpoint: getStopBySIP")
        print(f"  Status: {r.status_code}")
        print(f"  Body: {r.text[:200]}")
        
    except Exception as e:
        print(f"  Error: {e}")
        
    # 2. Try webGetStops (maybe it lists all? No, usually needs params)
    
    # 3. Try to use search autocomplete? (Not easy via API)

test_resolve("1307")
test_resolve("16008")
