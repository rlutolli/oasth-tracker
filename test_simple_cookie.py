
import requests

STATIC_TOKEN = "e2287129f7a2bbae422f3673c4944d703b84a1cf71e189f869de7da527d01137"
URL_HOME = "https://telematics.oasth.gr/en/"
URL_API = "https://telematics.oasth.gr/api/?act=getStopArrivals&p1=964"

session = requests.Session()
session.headers.update({
    "User-Agent": "Mozilla/5.0 (Linux; Android 10) AppleWebKit/537.36"
})

print("1. Getting Homepage for Cookie...")
try:
    resp = session.get(URL_HOME, timeout=10)
    print(f"Status: {resp.status_code}")
    print(f"Cookies: {session.cookies.get_dict()}")
    
    phpsessid = session.cookies.get("PHPSESSID")
    if not phpsessid:
        print("FAIL: No PHPSESSID received")
        exit(1)
        
    print(f"Got PHPSESSID: {phpsessid}")
    
    print("\n2. Testing API with this cookie and STATIC TOKEN...")
    headers = {
        "X-Requested-With": "XMLHttpRequest",
        "X-CSRF-Token": STATIC_TOKEN,
        "Referer": URL_HOME,
        "Origin": "https://telematics.oasth.gr"
    }
    
    resp_api = session.get(URL_API, headers=headers, timeout=10)
    print(f"API Status: {resp_api.status_code}")
    print(f"API Response: {resp_api.text[:100]}...")
    
    if resp_api.status_code == 200 and "btime2" in resp_api.text:
        print("SUCCESS! No WebView needed!")
    else:
        print("FAILED! Simple cookie is not enough.")
        
except Exception as e:
    print(f"Error: {e}")
