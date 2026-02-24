
import requests
import json

cookies = {'PHPSESSID': 'h2daist5tpv86h10aotc6lpch4'}
headers = {
    'X-Requested-With': 'XMLHttpRequest',
    'User-Agent': 'Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/142.0.0.0 Safari/537.36',
    'X-CSRF-Token': 'e2287129f7a2bbae422f3673c4944d703b84a1cf71e189f869de7da527d01137'
}

print("📥 Downloading Full Routes/Stops DB...")
# This call is what load on the homepage.
url = "https://telematics.oasth.gr/api/?act=webGetLinesWithMLAndRoutes"

try:
    r = requests.post(url, cookies=cookies, headers=headers)
    print(f"Status: {r.status_code}")
    print(f"Size: {len(r.text)} bytes")
    
    # Analyze Payload
    try:
        data = r.json()
        print(f"JSON Keys: {data.keys()}")
        
        # Usually it returns a list of lines, we need to see how to get STOPS from this.
        # Or maybe there is another endpoint "webGetStops" that returns ALL stops?
        
        # Let's search inside the text for "1307" regardless of JSON structure
        if "1307" in r.text:
            pos = r.text.find("1307")
            print(f"✅ Found '1307' at pos {pos}")
            snippet = r.text[max(0, pos-100):pos+100]
            print(f"   Context: ...{snippet}...")
        else:
            print("❌ '1307' NOT found in Lines DB.")
            
    except Exception as e:
        print(f"Parsing Error: {e}")
        
except Exception as e:
    print(f"Request Error: {e}")
