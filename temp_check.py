import requests
import json

cookies = {'PHPSESSID': 'h2daist5tpv86h10aotc6lpch4'}
headers = {
    'X-Requested-With': 'XMLHttpRequest',
    'User-Agent': 'Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/142.0.0.0 Safari/537.36',
    'X-CSRF-Token': 'e2287129f7a2bbae422f3673c4944d703b84a1cf71e189f869de7da527d01137',
    'Referer': 'https://telematics.oasth.gr/'
}

def get_stops(route_id):
    url = f"https://telematics.oasth.gr/api/?act=webGetStops&p1={route_id}"
    try:
        r = requests.post(url, cookies=cookies, headers=headers)
        return r.json()
    except:
        return []

def main():
    # User confusion check: Is there a StopCode 1403?
    # We can't search by StopCode directly, we have to find it on a line.
    
    # Let's check the partial output if we can't search easily.
    # Or just wait for the scrape.
    pass
    # Actually, we can use the previous check_stop_1306.py logic but modified.
    
if __name__ == "__main__":
    pass
