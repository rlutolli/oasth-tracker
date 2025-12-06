from playwright.sync_api import sync_playwright
import json
import sys

def search_stop(stop_code):
    print(f"Searching for Stop Code: {stop_code}...")
    
    with sync_playwright() as p:
        browser = p.chromium.launch(
            headless=True,
            args=['--no-sandbox', '--disable-setuid-sandbox']
        )
        page = browser.new_page()
        
        # Go to home
        page.goto('https://telematics.oasth.gr/')
        page.wait_for_load_state('networkidle')
        
        # Click the "Information Station" tab (collapseTwo)
        try:
            page.click('a[href="#collapseTwo"]', timeout=5000)
        except:
            print("Tab might be already open or selector changed.")

        # Fill search input
        page.fill('#stopSearch', str(stop_code))
        
        # Setup listener for the response that contains the internal ID/Redirect
        # When we click search, the site usually makes an API call like:
        # /api/?act=getStopNameAndId&p1=CODE
        # Let's try to capture that or any relevant response
        
        with page.expect_response(lambda r: "getStopNameAndId" in r.url or "getStopArrivals" in r.url, timeout=10000) as response_info:
            page.click('.toSearchForStop')
            
        response = response_info.value
        print(f"Captured Response URL: {response.url}")
        
        try:
            # URL is like: https://telematics.oasth.gr/api/?act=getStopArrivals&p1=3344
            # We want '3344'
            from urllib.parse import urlparse, parse_qs
            parsed = urlparse(response.url)
            params = parse_qs(parsed.query)
            
            p1 = params.get('p1', [None])[0]
            
            if p1:
                print(f"Found ID: {p1}")
                return p1
            else:
                print("Could not find p1 param")
                return None
                
        except Exception as e:
            print(f"Error parsing URL: {e}")
            return None
        finally:
            browser.close()

if __name__ == "__main__":
    if len(sys.argv) > 1:
        search_stop(sys.argv[1])
    else:
        # Default test
        search_stop("16008")
