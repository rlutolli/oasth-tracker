# OASTH Bus Widget

> Universal real-time bus arrival tracker for Thessaloniki (OASTH)

A lightweight, cross-platform library and CLI for fetching live bus arrivals from the OASTH telematics system. Works on **Linux**, **Android**, **iOS**, and more.

![Python Version](https://img.shields.io/badge/python-3.8%2B-blue)
![License](https://img.shields.io/badge/license-MIT-green)
![Platform](https://img.shields.io/badge/platform-universal-brightgreen)

## 🚀 Features

- **Fast**: 0.7s API calls (vs 5s+ with browser automation)
- **Lightweight**: ~2MB memory usage
- **Session Caching**: One-time browser init, cached for 1 hour
- **Multiple Outputs**: ANSI terminal, JSON, plain text
- **Cross-Platform**: Python core works anywhere

## 📦 Installation

```bash
git clone https://github.com/rlutolli/oasth-bus-widget.git
cd oasth-bus-widget

python3 -m venv venv
source venv/bin/activate

pip install playwright requests
playwright install firefox
```

## 🔧 CLI Usage

```bash
# Get arrivals for a stop (ANSI colored output)
python cli.py --stop 1029 --stop-name "ESPEROS"

# JSON output (for mobile apps/widgets)
python cli.py --stop 1052 --format json

# Plain text
python cli.py --stop 1049 --format plain

# List all bus lines
python cli.py --lines

# Clear session cache
python cli.py --clear-cache
```

## 🐍 Python API

```python
from core import get_arrivals, OasthAPI

# Quick function
arrivals = get_arrivals("1029")
for bus in arrivals:
    print(f"Line {bus.line_id}: {bus.estimated_minutes} min")

# Full API
api = OasthAPI()
lines = api.get_lines()
arrivals = api.get_arrivals("1052")
```

## 🖥️ Platform Integration

### Linux (KDE/GNOME/Polybar/Conky)

**KDE Plasma Widget:**
```bash
/path/to/venv/bin/python3 /path/to/cli.py --stop 1029
```
Set "Run Every" to 60 seconds. Use monospaced font.

**Polybar Module:**
```ini
[module/bus]
type = custom/script
exec = /path/to/venv/bin/python3 /path/to/cli.py --stop 1029 --format plain
interval = 60
```

### Android & iOS

Two approaches:

1. **Local Server** (Recommended for home use)
   - Run Python server on home PC/Raspberry Pi
   - Mobile app fetches JSON from `http://your-ip:8080/arrivals/1029`
   
2. **WebView Session** (Standalone app)
   - Hidden WebView loads OASTH site once
   - Extract session cookies, use native HTTP

**JSON endpoint for mobile:**
```bash
python cli.py --stop 1029 --format json
```

Returns:
```json
[
  {"line": "01", "description": "...", "minutes": 5, "vehicle": "1234"},
  {"line": "31", "description": "...", "minutes": 12, "vehicle": "5678"}
]
```

## 📁 Project Structure

```
oasth-bus-widget/
├── core/
│   ├── session.py    # Session management + caching
│   ├── api.py        # Pure HTTP API client
│   └── models.py     # Data structures
├── cli.py            # Command-line interface
├── bus_timer.py      # Legacy KDE widget script
└── README.md
```

## 🔬 How It Works

1. **Session Init** (once per hour): Headless Firefox extracts `PHPSESSID` + `token`
2. **Cache**: Credentials stored in `~/.cache/oasth_widget/session.json`
3. **Fast API**: Pure HTTP with cached credentials (~0.7s per call)

## 🚍 Finding Stop Codes

Use the official OASTH app or website to find stop codes. Example stops:
- `1029` - ESPEROS
- `1052` - KAMARA
- `1049` - IASONIDOU

## 🤝 Contributing

Contributions welcome! Areas to help:
- [ ] Android Kotlin wrapper
- [ ] iOS Swift wrapper
- [ ] Server mode (Flask/FastAPI)
- [ ] Homebridge/Home Assistant integration

## 📄 License

MIT License - See [LICENSE](LICENSE)
