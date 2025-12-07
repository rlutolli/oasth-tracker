import requests
import json
import sys

# Verified Session
cookies = {'PHPSESSID': 'h2daist5tpv86h10aotc6lpch4'}
headers = {
    'X-Requested-With': 'XMLHttpRequest',
    'User-Agent': 'Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/142.0.0.0 Safari/537.36',
    'X-CSRF-Token': 'e2287129f7a2bbae422f3673c4944d703b84a1cf71e189f869de7da527d01137',
    'Referer': 'https://telematics.oasth.gr/'
}

def get_lines():
    url = "https://telematics.oasth.gr/api/?act=webGetLines" 
    try:
        r = requests.post(url, cookies=cookies, headers=headers)
        return r.json()
    except Exception as e:
        print(f"Error lines: {e}")
        return []

def get_routes(line_id):
    url = f"https://telematics.oasth.gr/api/?act=webGetRoutes&p1={line_id}"
    try:
        r = requests.post(url, cookies=cookies, headers=headers)
        if not r.text.strip(): return []
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
    print("🔍 Searching for Stop 1306...")
    lines = get_lines()
    
    # User suspects 01N serves it, and 57?
    # Let's prioritize them but search all to be sure.
    target_stop_code = "1306"
    
    found_info = []

    for line in lines:
        line_code = line.get('LineCode')
        line_display = line.get('LineID')
        
        # Optimization: only check likely lines first if needed, but for 1306 let's be thorough
        # Or just check all fast.
        
        routes = get_routes(line_code)
        if not routes: continue
        
        for route in routes:
            route_id = route.get('RouteCode')
            stops = get_stops(route_id)
            if stops:
                for stop in stops:
                    sc = str(stop.get('StopCode'))
                    if sc == target_stop_code:
                        print(f"\n✅ FOUND Stop 1306 on Line {line_display} (Route {route_id})")
                        print(f"   Internal StopID: {stop.get('StopID')}")
                        print(f"   Description: {stop.get('StopDescr')}")
                        found_info.append({
                            'Line': line_display,
                            'Route': route_id,
                            'StopID': stop.get('StopID')
                        })

    if found_info:
        print("\nSummary for Stop 1306:")
        ids = set(f['StopID'] for f in found_info)
        print(f"Internal IDs found: {ids}")
        if len(ids) > 1:
            print("!!! CONFIRMED: Multiple Internal IDs for same Stop Code 1306!")
        else:
            print("Single Internal ID found so far.")
    else:
        print("❌ Stop 1306 not found (yet).")

if __name__ == "__main__":
    main()
