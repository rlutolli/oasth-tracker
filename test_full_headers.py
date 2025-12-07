
import requests

URL = "https://telematics.oasth.gr/en/"

headers = {
    'Accept': 'text/html,application/xhtml+xml,application/xml;q=0.9,image/avif,image/webp,image/apng,*/*;q=0.8,application/signed-exchange;v=b3;q=0.7',
    'Accept-Encoding': 'gzip, deflate, br',
    'Accept-Language': 'en-US,en;q=0.9',
    'Cache-Control': 'max-age=0',
    'Connection': 'keep-alive',
    'Host': 'telematics.oasth.gr',
    'Sec-Ch-Ua': '"Not_A Brand";v="8", "Chromium";v="120", "Google Chrome";v="120"',
    'Sec-Ch-Ua-Mobile': '?0',
    'Sec-Ch-Ua-Platform': '"Windows"',
    'Sec-Fetch-Dest': 'document',
    'Sec-Fetch-Mode': 'navigate',
    'Sec-Fetch-Site': 'none',
    'Sec-Fetch-User': '?1',
    'Upgrade-Insecure-Requests': '1',
    'User-Agent': 'Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36'
}

print("Requesting homepage with FULL headers...")
try:
    s = requests.Session()
    resp = s.get(URL, headers=headers, timeout=10)
    print(f"Status: {resp.status_code}")
    print(f"Cookies in jar: {s.cookies.get_dict()}")
    
    if 'PHPSESSID' in s.cookies:
        print("SUCCESS! Got PHPSESSID without WebView")
    else:
        print("FAILED. No PHPSESSID.")
        
except Exception as e:
    print(f"Error: {e}")
