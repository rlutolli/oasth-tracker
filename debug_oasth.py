import requests
import json
import sys

# Redirect stdout to a file to capture all output without truncation issues
sys.stdout = open('debug_output.txt', 'w', encoding='utf-8')

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
        print(f"Error fetching lines: {e}")
        return []

def get_routes(line_id):
    url = f"https://telematics.oasth.gr/api/?act=webGetRoutes&p1={line_id}"
    try:
        r = requests.post(url, cookies=cookies, headers=headers)
        # Check if response is empty string or not json
        if not r.text.strip():
            return None
        return r.json()
    except Exception as e:
        print(f"Error fetching routes for {line_id}: {e}")
        return []

def get_stops(route_id):
    url = f"https://telematics.oasth.gr/api/?act=webGetStops&p1={route_id}"
    try:
        r = requests.post(url, cookies=cookies, headers=headers)
        return r.json()
    except Exception as e:
        print(f"Error fetching stops for {route_id}: {e}")
        return []

def main():
    lines = get_lines()
    # User mentioned: 1N (internal 146?), 57 (internal 103?)
    # We want to find which field holds 146 and 103.
    
    target_display_codes = ["01N", "1N", "57"]
    
    print(f"Total Lines Found: {len(lines)}")
    if lines:
        print(f"First Line Sample in List: {lines[0]}")
    
    found_lines = [l for l in lines if str(l.get('LineID')) in target_display_codes or str(l.get('LineCode')) in target_display_codes or "57" in str(l.get('LineID'))]
    
    for line in found_lines:
        line_code = line.get('LineCode') # This is the internal numeric ID (e.g. 146 or 103)
        line_display = line.get('LineID') # "01N" or "57"

        print(f"\n==================================================")
        print(f"Line Object: {line}")
        
        # We concluded LineCode is the one to use
        print(f"--- Fecthing routes for Line {line_display} using Internal ID {line_code} ---")
        routes = get_routes(line_code)
        
        if routes:
            print(f"SUCCESS. Found {len(routes)} routes.")
            for r in routes:
               print(f"   Route: {r}")
               # Use RouteCode!
               rid = r.get('RouteCode') 
               stops = get_stops(rid)
               print(f"     Stops Found: {len(stops)}")
               if stops:
                   print(f"     First Stop Valid Keys: {list(stops[0].keys())}")
                   print(f"     First Stop: {stops[0]}")
                   
                   # Check user interest stops
                   for stop in stops:
                        stop_code = str(stop.get('StopCode'))
                        stop_id = str(stop.get('StopID')) # Assuming keys exist
                        if stop_code in ["1306", "1307", "10038"]:
                             print(f"    !!! Found User Interest Stop by Code: {stop}")
        else:
            print(f"FAILED to get routes for {line_code}")

if __name__ == "__main__":
    main()
