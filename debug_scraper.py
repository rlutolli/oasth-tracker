
import requests
import json
import time

# Fresh Session
cookies = {'PHPSESSID': 'l1mveev6u5puekb5cm6mfm85v6'}
headers = {
    'X-Requested-With': 'XMLHttpRequest',
    'User-Agent': 'Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/142.0.0.0 Safari/537.36',
    'X-CSRF-Token': 'e2287129f7a2bbae422f3673c4944d703b84a1cf71e189f869de7da527d01137'
}

def get_lines():
    uri = "https://telematics.oasth.gr/api/?act=webGetLines"
    try:
        r = requests.post(uri, cookies=cookies, headers=headers)
        return r.json()
    except Exception as e:
        print(f"Error lines: {e}")
        return None

def main():
    lines = get_lines()
    if not lines: return

    print(f"Lines Type: {type(lines)}")
    
    # Just inspect the first item raw
    if isinstance(lines, list):
         print(f"Lines is a LIST of {len(lines)} items.")
         if len(lines) > 0:
             print(f"First List Item: {lines[0]}")
    elif isinstance(lines, dict):
         keys = list(lines.keys())
         print(f"Lines is a DICT with {len(keys)} keys.")
         first_key = keys[0]
         val = lines[first_key]
         print(f"First Key: {first_key}")
         print(f"First Value: {val}")

if __name__ == "__main__":
    main()
