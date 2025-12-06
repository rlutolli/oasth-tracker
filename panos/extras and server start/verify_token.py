from playwright.sync_api import sync_playwright
import hashlib

def verify_token_formula():
    print("Verifying token formula...")
    
    with sync_playwright() as p:
        browser = p.chromium.launch(
            headless=True,
            args=['--no-sandbox', '--disable-setuid-sandbox']
        )
        page = browser.new_page()
        
        # Navigate to homepage to get cookie
        page.goto('https://telematics.oasth.gr/')
        page.wait_for_load_state('networkidle')
        
        # 1. Get PHPSESSID from cookies
        cookies = page.context.cookies()
        php_session = None
        for cookie in cookies:
            print(f"Cookie: {cookie['name']} = {cookie['value']}")
            if cookie['name'] == 'PHPSESSID':
                php_session = cookie['value']
                break
                
        if not php_session:
            print("ERROR: Could not find PHPSESSID cookie")
            browser.close()
            return
            
        print(f"PHPSESSID: {php_session}")
        
        # 2. Calculate expected token (SHA-256)
        expected_token = hashlib.sha256(php_session.encode()).hexdigest()
        print(f"Calculated Token (SHA-256): {expected_token}")
        
        # 3. Get actual token used by the page
        # We'll execute JS to see what the page uses in its headers structure
        # or grab it from a request if we trigger one
        
        print("Triggering API call to capture real token...")
        
        real_token = None
        
        def handle_request(request):
            nonlocal real_token
            if 'x-csrf-token' in request.headers:
                real_token = request.headers['x-csrf-token']
                
        page.on('request', handle_request)
        
        # Trigger navigation which fires API
        page.goto('https://telematics.oasth.gr/#stationInfo_3344')
        page.wait_for_timeout(5000)
        
        if real_token:
            print(f"Actual Token from Header:   {real_token}")
            
            if real_token == expected_token:
                print("\nSUCCESS! Token formula confirmed: SHA-256(PHPSESSID)")
            else:
                print("\nFAILURE! Tokens do not match.")
        else:
            print("Could not capture real token from requests.")
            
        browser.close()

if __name__ == "__main__":
    verify_token_formula()
