# OASTH Live 🚌

Real-time bus arrivals for Thessaloniki, Greece — as a home screen widget!

<p align="center">
  <img src="https://img.shields.io/github/v/release/rlutolli/oasth-tracker?style=flat-square" alt="Release">
  <img src="https://img.shields.io/github/license/rlutolli/oasth-tracker?style=flat-square" alt="License">
</p>

---

## 📱 For Users

### Download & Install

1. **Download** the latest APK from [Releases](https://github.com/rlutolli/oasth-tracker/releases)
2. **Install** the APK on your Android phone
   - You may need to enable "Install from unknown sources"
3. **Open** the app to establish a session
4. **Add widget** to your home screen:
   - Long-press your home screen
   - Tap "Widgets"
   - Find "OASTH Live" and drag it
5. **Configure** with your stop code (e.g., `1029`)
6. **Tap widget** anytime to refresh!

### Finding Your Stop Code

1. Visit [telematics.oasth.gr](https://telematics.oasth.gr/en/)
2. Search for your bus stop
3. The stop code is shown on the stop marker

### Supported Languages

- 🇬🇧 English
- 🇬🇷 Greek (Ελληνικά)

The app follows your phone's language setting.

---

## 🔧 For Developers

### How It Works

This app reverse-engineers the OASTH telematics API:

1. **Session**: A hidden WebView visits the OASTH site to get a `PHPSESSID` cookie and CSRF token
2. **API Calls**: Native HTTP requests with the session credentials fetch real-time arrivals
3. **Widget**: `AppWidgetProvider` with `Executor` pattern for background work

### API Endpoints

```
GET https://telematics.oasth.gr/api/?act=getStopArrivals&p1={stopCode}

Headers:
  Cookie: PHPSESSID={sessionId}
  X-CSRF-Token: {token}
  X-Requested-With: XMLHttpRequest
```

### Project Structure

```
android/
├── app/src/main/java/com/oasth/widget/
│   ├── data/
│   │   ├── Models.kt          # Data classes
│   │   ├── SessionManager.kt  # WebView session handling
│   │   ├── OasthApi.kt        # HTTP API client
│   │   └── WidgetConfigRepository.kt
│   ├── widget/
│   │   ├── BusWidgetProvider.kt  # Home screen widget
│   │   └── WidgetConfigActivity.kt
│   └── ui/
│       └── MainActivity.kt
├── res/
│   ├── values/strings.xml     # English strings
│   ├── values-el/strings.xml  # Greek strings
│   └── layout/
└── build.gradle.kts
```

### Building

```bash
cd android
./gradlew assembleDebug
# APK at: app/build/outputs/apk/debug/app-debug.apk
```

### Key Learnings

- **RemoteViews limitations**: Only supports specific layouts (`LinearLayout`, `RelativeLayout`, etc.) and widgets (`TextView`, `ImageView`, `Button`). NO plain `<View>` elements!
- **Widget async**: Use `Executor` + `Handler` pattern, NOT coroutines in `AppWidgetProvider`
- **Samsung quirks**: Test on Samsung devices - they have stricter widget requirements

---

## 📋 Python CLI (Bonus)

A Python CLI is also included for desktop/terminal use:

```bash
# Install dependencies
pip install requests playwright
playwright install firefox

# Get arrivals for a stop
python cli.py --stop 1029

# List all bus lines
python cli.py --lines
```

---

## 🤝 Contributing

Pull requests welcome! Feel free to:
- Report bugs
- Suggest features
- Add translations

---

## 📜 License

MIT License — see [LICENSE](LICENSE)

---

<p align="center">
  Made with ♥ by a fellow duo enthusiast
</p>
