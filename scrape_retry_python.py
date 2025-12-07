
import requests
import json
import time

# Fresh Session from Browser Subagent
cookies = {'PHPSESSID': 'l1mveev6u5puekb5cm6mfm85v6'}

headers = {
    'Host': 'telematics.oasth.gr',
    'Connection': 'keep-alive',
    'Content-Length': '0',
    'sec-ch-ua-platform': '"Windows"',
    'X-Requested-With': 'XMLHttpRequest',
    'User-Agent': 'Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/142.0.0.0 Safari/537.36',
    'Accept': 'application/json, text/javascript, */*; q=0.01',
    'X-CSRF-Token': 'e2287129f7a2bbae422f3673c4944d703b84a1cf71e189f869de7da527d01137',
    'Origin': 'https://telematics.oasth.gr',
    'Sec-Fetch-Site': 'same-origin',
    'Sec-Fetch-Mode': 'cors',
    'Sec-Fetch-Dest': 'empty',
    'Referer': 'https://telematics.oasth.gr/',
    'Accept-Encoding': 'gzip, deflate, br, zstd',
    'Accept-Language': 'en-US,en;q=0.9',
}

def get_lines():
    print("🚌 Fetching All Lines...")
    url = "https://telematics.oasth.gr/api/?act=webGetLines" 
    try:
        r = requests.post(url, cookies=cookies, headers=headers)
        if r.status_code != 200:
            print(f"   ❌ Status: {r.status_code}")
            print(f"   Body: {r.text[:200]}")
            return []
            
        data = r.json()
        print(f"   ✅ Success. Found {len(data)} lines.")
        return data
    except Exception as e:
        print(f"   Error: {e}")
        return []

def get_routes(line_id):
    url = f"https://telematics.oasth.gr/api/?act=webGetRoutes&p1={line_id}"
    try:
        r = requests.post(url, cookies=cookies, headers=headers)
        return r.json()
    except:
        return []

def get_stops(route_id):
    url = f"https://telematics.oasth.gr/api/?act=webGetStops&p1={route_id}"
    try:
        r = requests.post(url, cookies=cookies, headers=headers)
        return r.json()
    except:
        return []

def main():
    lines = get_lines()
    if not lines: 
        print("Aborting.")
        return

    # Process first valid line
    for line in lines:
        line_id = line.get('LineID') # Verified Key
        line_code = line.get('LineCode')
        
        print(f"👉 Line {line_code} (ID: {line_id})")
        
        routes = get_routes(line_id)
        if not routes:
             print("   ❌ No routes (or API error)")
             continue
             
        print(f"   ✅ Found {len(routes)} routes.")
        
        for route in routes:
            # We need to verify Route Keys too!
            # Usually: RouteID, RouteCode...
            # Let's verify by printing keys if success
            print(f"   Route Object Keys: {list(route.keys())}")
            
            # Assuming 'RouteID' based on pattern
            r_id = route.get('RouteID') 
            print(f"   -> Route {r_id}")
            
            stops = get_stops(r_id)
            print(f"      Found {len(stops)} stops.")
            
            if stops:
                print(f"      Stop Keys: {list(stops[0].keys())}")
                print(f"      First Stop: {stops[0]}")
                return # SUCCESS - we have all info needed

if __name__ == "__main__":
    main()
