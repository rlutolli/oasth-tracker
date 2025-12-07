
import requests

PHPSESSID = "oia2777qldiqp2m9d2bj9hmoj2"
TOKEN = "e2287129f7a2bbae422f3673c4944d703b84a1cf71e189f869de7da527d01137"
URL = "https://telematics.oasth.gr/api/?act=getStopArrivals&p1=3344"

headers = {
    "User-Agent": "Mozilla/5.0 (Linux; Android 10) AppleWebKit/537.36",
    "Accept": "application/json, text/javascript, */*; q=0.01",
    "X-Requested-With": "XMLHttpRequest",
    "X-CSRF-Token": TOKEN,
    "Cookie": f"PHPSESSID={PHPSESSID}",
    "Referer": "https://telematics.oasth.gr/en/",
    "Origin": "https://telematics.oasth.gr"
}

print(f"Testing Specific Session...")
print(f"Cookie: {PHPSESSID}")
try:
    resp = requests.get(URL, headers=headers, timeout=10)
    print(f"Status: {resp.status_code}")
    print(f"Response: {resp.text[:200]}")
    if resp.status_code == 200:
        print("SUCCESS!")
    else:
        print("FAILED!")
except Exception as e:
    print(f"Error: {e}")
