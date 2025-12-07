
import requests
import json

cookies = {'PHPSESSID': 'h2daist5tpv86h10aotc6lpch4'}
headers = {
    'X-Requested-With': 'XMLHttpRequest',
    'User-Agent': 'Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/142.0.0.0 Safari/537.36',
    'X-CSRF-Token': 'e2287129f7a2bbae422f3673c4944d703b84a1cf71e189f869de7da527d01137',
    'Origin': 'https://telematics.oasth.gr',
    'Referer': 'https://telematics.oasth.gr/'
}

def test_search(query):
    # This URL is the most likely candidate for "Search" autocomplete
    url = f"https://telematics.oasth.gr/api/?act=getStopNameAndXY&p1={query}"
    
    print(f"\n🔎 Testing Search for '{query}'...")
    try:
        r = requests.post(url, cookies=cookies, headers=headers)
        print(f"Status: {r.status_code}")
        print(f"Body: {r.text[:300]}")
    except Exception as e:
        print(f"Error: {e}")

test_search("1307")
test_search("ΚΟΛΟΜΒΟΥ") # Greek name for 1307
