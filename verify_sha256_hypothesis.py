
import requests
import hashlib

PHPSESSID = "oia2777qldiqp2m9d2bj9hmoj2"
URL = "https://telematics.oasth.gr/api/?act=getStopArrivals&p1=964"

# Calculate SHA-256 of PHPSESSID
token_hash = hashlib.sha256(PHPSESSID.encode()).hexdigest()
print(f"PHPSESSID: {PHPSESSID}")
print(f"Calculated Token (SHA256): {token_hash}")

headers_sha = {
    "User-Agent": "Mozilla/5.0 (Linux; Android 10) AppleWebKit/537.36",
    "Accept": "application/json, text/javascript, */*; q=0.01",
    "X-Requested-With": "XMLHttpRequest",
    "X-CSRF-Token": token_hash,
    "Cookie": f"PHPSESSID={PHPSESSID}",
    "Referer": "https://telematics.oasth.gr/en/",
    "Origin": "https://telematics.oasth.gr"
}

headers_static = headers_sha.copy()
headers_static["X-CSRF-Token"] = "e2287129f7a2bbae422f3673c4944d703b84a1cf71e189f869de7da527d01137"

def test(name, hdrs):
    print(f"\nTesting {name}...")
    try:
        resp = requests.get(URL, headers=hdrs, timeout=10)
        print(f"Status: {resp.status_code}")
        print(f"Response: {resp.text[:100]}...")
    except Exception as e:
        print(f"Error: {e}")

test("SHA-256 Token", headers_sha)
test("Static Token", headers_static)
