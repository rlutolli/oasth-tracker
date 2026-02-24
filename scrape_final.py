
import requests
import json
import time

# Fresh Session (Browser verified)
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
    'Referer': 'https://telematics.oasth.gr/',
}

# The Output Map
# "1307": "916"
stop_map = {}

def get_lines():
    print("🚌 Fetching All Lines...")
    try:
        r = requests.post("https://telematics.oasth.gr/api/?act=webGetLines", cookies=cookies, headers=headers)
        return r.json()
    except Exception as e:
        print(f"Error lines: {e}")
        return []

def get_routes(line_id):
    try:
        r = requests.post(f"https://telematics.oasth.gr/api/?act=webGetRoutes&p1={line_id}", cookies=cookies, headers=headers)
        return r.json()
    except:
        return []

def get_stops(route_id):
    try:
        r = requests.post(f"https://telematics.oasth.gr/api/?act=webGetStops&p1={route_id}", cookies=cookies, headers=headers)
        return r.json()
    except:
        return []

def main():
    lines = get_lines()
    if not lines: return 
    
    print(f"Found {len(lines)} lines. Scraping ALL stops (Ctrl+C to stop)...")
    
    count = 0
    total_stops = 0
    
    for i, line in enumerate(lines):
        line_id = line.get('LineID')
        line_code = line.get('LineCode')
        
        print(f"Processing Line {i+1}/{len(lines)}: {line_code}...", end="\r")
        
        if not line_id: continue
        
        routes = get_routes(line_id)
        for route in routes:
            # Verified Key: RouteCode
            r_code = route.get('RouteCode') 
            if not r_code: continue
            
            stops = get_stops(r_code)
            
            for stop in stops:
                # We need to determine keys dynamically on first success
                # but let's try standard guesses
                s_code = stop.get('StopCode') # e.g. 1307
                s_id = stop.get('StopID')     # e.g. 916
                s_descr = stop.get('StopDescr')
                
                if s_code and s_id:
                    stop_map[s_code] = s_id
                    total_stops += 1
                    # Debug print occasionally
                    if total_stops % 50 == 0:
                         print(f"  [Mapped {total_stops} stops] Last: {s_code}->{s_id} ({s_descr})")
        
        # Save progress every 10 lines
        if i % 10 == 0:
            with open("stops.json", "w", encoding="utf-8") as f:
                json.dump(stop_map, f, ensure_ascii=False)
            
    # Final Save
    with open("stops.json", "w", encoding="utf-8") as f:
        json.dump(stop_map, f, ensure_ascii=False)
    
    print(f"\n\nDONE! Mapped {total_stops} unique stops. Saved to stops.json")

if __name__ == "__main__":
    main()
