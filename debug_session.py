
import sys
import traceback

try:
    print("Importing core...")
    from core.session import get_session
    from core.api import OasthAPI
    
    print("Getting session...")
    session = get_session(force_refresh=True)
    print(f"Session obtained: {session.phpsessid}")
    print(f"Session obtained: {session.phpsessid}")
    with open("token.txt", "w") as f:
        f.write(session.token)
    print(f"NEW TOKEN written to token.txt")
    
    print("Initializing API...")
    api = OasthAPI(session_data=session)
    
    print("Fetching stop 916...")
    arrivals = api.get_arrivals("916")
    print(f"Arrivals count: {len(arrivals)}")
    print(arrivals)

except Exception:
    with open("debug_error.log", "w", encoding="utf-8") as f:
        f.write("CRASHED!\n")
        traceback.print_exc(file=f)
    print("CRASHED! See debug_error.log")
