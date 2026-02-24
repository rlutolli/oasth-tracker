import time

def main():
    print("🔍 Monitoring scraper log for Street ID 1403...")
    target_found = False
    
    # We will loop and read the file
    for _ in range(30): # 30 seconds max wait for invalid checking
        try:
            with open('scraper_corrected.log', 'r', encoding='utf-8') as f:
                content = f.read()
                # Check if we see the Multi-ID log or just processing lines
                if "Multi-ID Stop Found: StreetID 1403" in content:
                    print("Found variation for StreetID 1403!")
                    print(content.split("Multi-ID Stop Found: StreetID 1403")[1].split("\n")[0])
                    target_found = True
                    break
        except:
            pass
        time.sleep(1)
        
    if not target_found:
        print("Waiting for log population...")

if __name__ == "__main__":
    main()
