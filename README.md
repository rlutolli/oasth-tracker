OASTH KDE Live Tracker (Thessaloniki Bus Widget)

This repository contains a low-resource, self-healing Python script designed to fetch real-time bus arrival data for OASTH (Thessaloniki) and display it cleanly on a KDE Plasma desktop using the Command Output widget.

The script bypasses the public website's anti-bot/session security checks by using a headless browser to establish a valid connection, keeping the widget reliable and low on resource usage between updates.

![Python Version](https://img.shields.io/badge/python-3.9%2B-blue)
![License](https://img.shields.io/badge/license-MIT-green)

You must have Git and Python 3 installed, along with the venv utility for environment isolation.

# 1. Install necessary Python environment tool
sudo apt install python3-venv

Follow these steps to set up the necessary environment and download the Playwright browser engine.

Clone the Repository and Navigate:

git clone [https://github.com/YOUR_USERNAME/kde-oasth-live-tracker.git](https://github.com/YOUR_USERNAME/kde-oasth-live-tracker.git)
cd kde-oasth-live-tracker

Create and Activate Virtual Environment (Sandbox):
This creates an isolated environment (venv) where all dependencies are installed.

```bash
python3 -m venv venv
source venv/bin/activate
```

Install Python Dependencies:
(This installs Playwright and necessary libraries.)

```bash
pip install -r requirements.txt
```

Install the Browser Engine (Critical Step):
Playwright requires its own separate, clean browser engine. We install the stable Firefox engine to avoid conflicts with Ubuntu's Chromium Snap.

```bash
playwright install firefox
```

Once the setup is complete, configure your KDE widget to run the script.

Add Widget: Right-click the desktop → Add Widgets → Search for and add "Command Output".

Crucial Setup: You must configure your widget's font for the alignment to work correctly.

Right-click the Widget → Configure → Go to Appearance/Font Settings.

Select a Monospaced Font (e.g., Hack, Noto Mono, Ubuntu Mono).

Configure Command: In the widget settings, paste the following command. You must replace /path/to/project/ with the absolute path to your cloned folder (e.g., /home/username/kde-oasth-live-tracker/).

```bash
/path/to/project/venv/bin/python3 /path/to/project/bus_timer.py
```

Final Polish:

Set Run Every to 60 seconds or higher.

The output will display using ANSI color codes (Red for urgent, Green for safe) in a clean, terminal-like list format.

 - Fetches real-time bus data
 - Displays data on KDE Plasma desktop
 - Low resource usage
 - Self-healing mechanism for reliability

I actually welcome contributions to enhance this since I am not very energetic enhancing this. Please follow these steps:
1. Fork the repository.
2. Create a new branch for your feature or bug fix.
3. Submit a pull request with a description of your changes.

This project is licensed under the MIT License. See the [LICENSE](LICENSE) file for details.
