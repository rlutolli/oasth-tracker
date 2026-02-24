
import requests
import json

# Fresh session
cookies = {'PHPSESSID': 'h2daist5tpv86h10aotc6lpch4'}
headers = {
    'X-Requested-With': 'XMLHttpRequest',
    'User-Agent': 'Mozilla/5.0 (Linux; Android 10)'
}

def search_stop(external_query):
    print(f"\n🔍 Searching for: {external_query}")
    
    # 1. Search by Name/Code
    url = "https://telematics.oasth.gr/api/?act=webGetLinesWithMLAndRoutes"
    try:
        r = requests.post(url, cookies=cookies, headers=headers)
        data = r.json()
        
        # This endpoint returns ALL stops? No, usually just lines.
        # Let's try getStops (returns all stops for a line).
        # But we don't know the line.
        
        # Better: use the autocomplete endpoint if exists?
        # Actually OASTH uses `getStopBySIP` which takes STOP CODE? or `webGetStops`?
        
        # Let's try getStopBySIP which usually takes the "External" ID (SIP = Stop Info Provider?)
        # Or maybe the "Code" on screen is the SIP?
        
        search_urls = [
            f"https://telematics.oasth.gr/api/?act=getStopBySIP&p1={external_query}", # Try external ID here
            f"https://telematics.oasth.gr/api/?act=getStopNameAndXY&p1={external_query}" # Try Assuming it is Internal
        ]
        
        for i, s_url in enumerate(search_urls):
            print(f"  Attempt {i+1}: {s_url}")
            resp = requests.post(s_url, cookies=cookies, headers=headers)
            print(f"    Status: {resp.status_code}, Body: {resp.text[:100]}")
            
    except Exception as e:
        print(f"Error: {e}")

# Try known values
search_stop("1307")   # Is this external?
search_stop("16008")  # User said this is external
search_stop("3344")   # User said this is internal
