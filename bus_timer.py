from playwright.sync_api import sync_playwright
import sys

# CONFIGURATION
STOP_CODE = "..." # e.g., "3005", put your own stop code here
STATION_NAME = "..." # e.g., "PARKO SMYRNIS", put your own station name here

# --- ANSI COLORS ---
R  = '\033[0m'       # Reset
NEON_G = '\033[92m'  # Neon Green (Safe)
NEON_R = '\033[91m'  # Neon Red (Urgent)
AMBER  = '\033[93m'  # Amber/Yellow (Bus Line)
CYAN   = '\033[96m'  # Cyan (Header)
GREY   = '\033[90m'  # Dark Grey

def get_bus_times():
    try:
        with sync_playwright() as p:
            # 1. Launch Diet Browser
            browser = p.firefox.launch(headless=True, args=["--no-sandbox", "--disable-dev-shm-usage"])
            context = browser.new_context(viewport={"width": 1280, "height": 720}, service_workers="block")
            page = context.new_page()
            page.route("**/*.{png,jpg,jpeg,gif,svg,css,woff,woff2}", lambda route: route.abort())

            # 2. Go to site (Error handling removed for brevity)
            page.goto("https://telematics.oasth.gr/en/", timeout=15000)

            # 3. Search
            try:
                page.wait_for_selector("input[type='text']", timeout=10000)
                inputs = page.locator("input[type='text']").all()
                inputs[-1].fill(STOP_CODE)
                page.locator(".input-group-btn button").last.click()
            except:
                page.keyboard.press("Enter")

            # 4. Wait
            try:
                page.wait_for_selector(".arrivalContainer", timeout=10000)
            except:
                if "No info" in page.content():
                    print(f"{CYAN}{STATION_NAME} {AMBER}{STOP_CODE}{R} - No Buses")
                    browser.close()
                    return

            # 5. Scrape
            bus_data = {}
            rows = page.locator("li.arrivalContainer").all()

            for row in rows:
                if not row.is_visible(): continue
                try:
                    line = row.locator(".arrivalNumBtn").first.inner_text(timeout=1000).strip()
                    time_text = row.locator(".arrivalsAr").first.inner_text(timeout=1000).strip()
                    minutes_str = time_text.replace("'", "").strip()

                    if minutes_str.isdigit():
                        m = int(minutes_str)
                        if m > 0:
                            if line not in bus_data: bus_data[line] = set()
                            bus_data[line].add(m)
                except: continue

            browser.close()

            # 6. Sort
            if not bus_data:
                print(f"{CYAN}{STATION_NAME} {AMBER}{STOP_CODE}{R} - No Active Buses")
                return

            final_list = []
            for line, times_set in bus_data.items():
                sorted_times = sorted(list(times_set))
                final_list.append({"line": line, "times": sorted_times, "next": sorted_times[0]})

            final_list.sort(key=lambda x: x["next"])

            # 7. PRINT CLEAN LIST UI

            # Header: for e.g. PARKO SMYRNIS - 3005
            print(f"{CYAN}{STATION_NAME} {GREY}{STOP_CODE}{R}")
            print(f"{GREY}--------------------{R}") # Simple visual separator

            for item in final_list:
                line = item['line']
                times = item["times"][:2] # Limit to 2 times

                line_padded = f"{line:<5}"

                time_str_colored = ""
                for i, t in enumerate(times):
                    # Color Logic
                    color = NEON_R if t < 5 else NEON_G

                    # Separator: Use comma for multiple times
                    sep = f"{GREY},{R} " if i < len(times)-1 else ""

                    time_str_colored += f"{color}{t}{sep}"

                # Final Row Print: e.g., " 01M  -  3, 15 "
                print(f" {AMBER}{line_padded} {GREY}-{R} {time_str_colored}{R}")

    except Exception as e:
        print(f"Err: {str(e)[:15]}")

if __name__ == "__main__":
    get_bus_times()
