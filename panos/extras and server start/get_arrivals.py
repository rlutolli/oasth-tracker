from playwright.sync_api import sync_playwright
import json
import time

def get_arrivals(stop_id):
    print(f"Fetching arrivals for Stop ID: {stop_id}...")
    
    with sync_playwright() as p:
        browser = p.chromium.launch(
            headless=True,
            args=['--no-sandbox', '--disable-setuid-sandbox']
        )
        context = browser.new_context(
            user_agent='Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/91.0.4472.124 Safari/537.36',
            viewport={'width': 1920, 'height': 1080}
        )
        page = context.new_page()
        
        # 1. Load Home Page first
        print("Loading homepage...")
        page.goto('https://telematics.oasth.gr/')
        page.wait_for_load_state('networkidle')
        
        # 2. Setup expectation for the API call
        print("Waiting for API response...")
        
        # We use a lambda to match the URL precisely
        predicate = lambda response: "getStopArrivals" in response.url and response.status == 200

        with page.expect_response(predicate, timeout=15000) as response_info:
            # 3. Trigger the call by navigating
            target_url = f'https://telematics.oasth.gr/#stationInfo_{stop_id}'
            print(f"Navigating to {target_url}...")
            page.goto(target_url)
            
        # If we get here, the response was captured
        response = response_info.value
        print(f"Captured response from: {response.url}")
        print(f"Status: {response.status}")
        try:
            print("Headers:", response.all_headers())
        except:
            pass
            
        try:
            text = response.text()
            print(f"Response Text (first 100 chars): '{text[:100]}'")
            return response.json()
        except Exception as e:
            print(f"Error processing response: {e}")
            return None
        finally:
            browser.close()

if __name__ == "__main__":
    # Internal ID for 16008 is 3344
    STOP_ID = 3344
    
    try:
        data = get_arrivals(STOP_ID)
        
        if data:
            print("\nSUCCESS! Data received:")
            print(json.dumps(data, indent=2, ensure_ascii=False))
        else:
            print("\nFailed to retrieve data.")
    except Exception as e:
        print(f"\nScript Error: {e}")
