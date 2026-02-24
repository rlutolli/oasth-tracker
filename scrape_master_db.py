import requests
import json
import time

# Verified Session
cookies = {'PHPSESSID': 'h2daist5tpv86h10aotc6lpch4'}
headers = {
    'X-Requested-With': 'XMLHttpRequest',
    'User-Agent': 'Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/142.0.0.0 Safari/537.36',
    'X-CSRF-Token': 'e2287129f7a2bbae422f3673c4944d703b84a1cf71e189f869de7da527d01137',
    'Referer': 'https://telematics.oasth.gr/'
}

# KEY: StopID (Street/User facing, e.g. "1403")
# VALUE: Object with internal API info
# Structure:
# {
#   "1403": {
#       "StreetID": "1403",
#       "StopDescr": "TZAVELLA",
#       "API_IDs": ["1306"]  # List, just in case multiple API IDs map to same street stop
#   }
# }
stops_db = {} 

def get_lines():
    print("Fetching All Lines...")
    url = "https://telematics.oasth.gr/api/?act=webGetLines" 
    try:
        r = requests.post(url, cookies=cookies, headers=headers)
        data = r.json()
        print(f"   Success. Found {len(data)} lines.")
        return data
    except Exception as e:
        print(f"   Error: {e}")
        return []

def get_routes(line_id):
    # line_id must be the internal numeric ID (e.g. 146 for 01N)
    url = f"https://telematics.oasth.gr/api/?act=webGetRoutes&p1={line_id}"
    try:
        r = requests.post(url, cookies=cookies, headers=headers)
        if not r.text.strip():
            return []
        return r.json()
    except Exception as e:
        print(f"   Error fetching routes for {line_id}: {e}")
        return []

def get_stops(route_id):
    url = f"https://telematics.oasth.gr/api/?act=webGetStops&p1={route_id}"
    try:
        r = requests.post(url, cookies=cookies, headers=headers)
        if not r.text.strip():
            return []
        return r.json()
    except:
        return []

def main():
    lines = get_lines()
    if not lines: return

    total_lines = len(lines)
    stop_counter = 0
    
    try:
        for i, line in enumerate(lines):
            line_internal_id = line.get('LineCode') 
            line_display_id = line.get('LineID')
            
            if not line_internal_id: 
                print(f"Skipping line {i} (No Internal ID)")
                continue
                
            print(f"[{i+1}/{total_lines}] Processing Line: {line_display_id} (Internal: {line_internal_id})")
            
            routes = get_routes(line_internal_id)
            if not routes:
                print(f"  No routes found for {line_display_id}.")
                continue
                
            for route in routes:
                route_id = route.get('RouteCode')
                stops = get_stops(route_id)
                if stops:
                    for stop in stops:
                        # CORRECTION BASED ON USER FEEDBACK:
                        # StopID = External / Street ID (e.g. 1403)
                        # StopCode = Internal / API ID (e.g. 1306)
                        
                        street_id = str(stop.get('StopID')) 
                        api_id = str(stop.get('StopCode'))
                        stop_descr = stop.get('StopDescr')
                        
                        if street_id not in stops_db:
                            stops_db[street_id] = {
                                'StreetID': street_id,
                                'StopDescr': stop_descr,
                                'API_IDs': [] 
                            }
                        
                        # Add API ID if not already known for this StreetID
                        if api_id not in stops_db[street_id]['API_IDs']:
                            stops_db[street_id]['API_IDs'].append(api_id)
                            stop_counter += 1
                            # Log collisions/variations for verification
                            if len(stops_db[street_id]['API_IDs']) > 1:
                                print(f"     [INFO] Multi-ID Stop Found: StreetID {street_id} ({stop_descr}) -> API IDs {stops_db[street_id]['API_IDs']}")

            # Be polite to the server
            # time.sleep(0.05) 
            
    except KeyboardInterrupt:
        print("\n\nInterrupted by User. Saving partial results...")
    except Exception as e:
        print(f"\n\nError: {e}. Saving partial results...")
    finally:
        # Save Database
        print(f"\nScrape Ended. Saving {len(stops_db)} unique stops to stops.json...")
        with open('stops.json', 'w', encoding='utf-8') as f:
            json.dump(stops_db, f, ensure_ascii=False, indent=2)
        print("Done.")

if __name__ == "__main__":
    main()
