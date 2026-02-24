
import requests
import json

cookies = {'PHPSESSID': 'h2daist5tpv86h10aotc6lpch4'}
headers = {
    'X-Requested-With': 'XMLHttpRequest',
    'User-Agent': 'Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/142.0.0.0 Safari/537.36',
    'X-CSRF-Token': 'e2287129f7a2bbae422f3673c4944d703b84a1cf71e189f869de7da527d01137',
    'Referer': 'https://telematics.oasth.gr/'
}

def try_endpoint(name, act, param):
    url = f"https://telematics.oasth.gr/api/?act={act}&p1={param}"
    print(f"\n🧪 {name} ({act}, {param})")
    try:
        r = requests.post(url, cookies=cookies, headers=headers, timeout=5)
        print(f"   Status: {r.status_code}")
        try:
            sample = r.text[:200].replace('\n', '')
            print(f"   Body: {sample}")
        except:
            pass
        return r.text
    except Exception as e:
        print(f"   Error: {e}")
        return ""

# Try to find "1307" or "01307" mappings
# 1. webGetRoutesDetailsAndStops - maybe returns lists?
# 2. webGetStops - usually needs a line ID...
# 3. getStopNameAndXY - likely internal ID only

# Let's brute force a few search-like candidates
try_endpoint("Autocomplete", "getStopNameAndXY", "1307")
try_endpoint("Autocomplete Internal", "getStopNameAndXY", "916") # To confirm what it does

# The global search might be doing client-side filtering? 
# or maybe "webGetLinesWithMLAndRoutes" returns EVERYTHING?
try_endpoint("All Lines", "webGetLinesWithMLAndRoutes", "")

# Try searching for the code as if it was a line?
try_endpoint("Stop SIP", "getStopBySIP", "1307") 
