#!/usr/bin/env python3
"""
OASTH CLI Client
================
Command-line interface for getting bus arrivals.

Usage:
    python cli.py --stop 3344
    python cli.py --stop 3344 --format json
    python cli.py --lines
"""

import argparse
import json
import sys
from typing import List

from core import OasthAPI, BusArrival, get_arrivals

# ANSI Colors
R = '\033[0m'       # Reset
NEON_G = '\033[92m' # Green
NEON_R = '\033[91m' # Red
AMBER = '\033[93m'  # Yellow
CYAN = '\033[96m'   # Cyan
GREY = '\033[90m'   # Grey


def format_ansi(arrivals: List[BusArrival], stop_code: str, stop_name: str = "") -> str:
    """Format arrivals with ANSI colors for terminal"""
    lines = []
    
    # Header
    header = f"{CYAN}{stop_name}{R}" if stop_name else ""
    header += f" {GREY}{stop_code}{R}" if stop_code else ""
    lines.append(header.strip())
    lines.append(f"{GREY}{'─' * 25}{R}")
    
    if not arrivals:
        lines.append(f"{GREY}No buses{R}")
        return "\n".join(lines)
    
    # Group by line
    by_line = {}
    for a in arrivals:
        if a.line_id not in by_line:
            by_line[a.line_id] = []
        by_line[a.line_id].append(a.estimated_minutes)
    
    # Sort by next arrival
    sorted_lines = sorted(by_line.items(), key=lambda x: min(x[1]))
    
    for line_id, times in sorted_lines:
        times = sorted(times)[:2]  # Max 2 times per line
        
        line_padded = f"{line_id:<5}"
        
        time_parts = []
        for t in times:
            color = NEON_R if t < 5 else NEON_G
            time_parts.append(f"{color}{t}{R}")
        
        time_str = f"{GREY},{R} ".join(time_parts)
        lines.append(f" {AMBER}{line_padded}{R} {GREY}│{R} {time_str}")
    
    return "\n".join(lines)


def format_json(arrivals: List[BusArrival]) -> str:
    """Format arrivals as JSON"""
    data = [
        {
            "line": a.line_id,
            "description": a.line_descr,
            "minutes": a.estimated_minutes,
            "vehicle": a.vehicle_code
        }
        for a in arrivals
    ]
    return json.dumps(data, indent=2, ensure_ascii=False)


def format_plain(arrivals: List[BusArrival]) -> str:
    """Format arrivals as plain text"""
    if not arrivals:
        return "No buses"
    
    lines = []
    for a in arrivals:
        lines.append(f"{a.line_id}: {a.estimated_minutes} min")
    return "\n".join(lines)


def main():
    parser = argparse.ArgumentParser(
        description="OASTH Bus Arrival Widget",
        formatter_class=argparse.RawDescriptionHelpFormatter,
        epilog="""
Examples:
  %(prog)s --stop 3344           Get arrivals for stop 3344
  %(prog)s --stop 3344 --json    Output as JSON
  %(prog)s --lines               List all bus lines
  %(prog)s --clear-cache         Clear session cache
        """
    )
    
    parser.add_argument('--stop', '-s', type=str, help='Stop code to query')
    parser.add_argument('--stop-name', type=str, default='', help='Stop name for display')
    parser.add_argument('--format', '-f', choices=['ansi', 'json', 'plain'], 
                        default='ansi', help='Output format')
    parser.add_argument('--lines', action='store_true', help='List all bus lines')
    parser.add_argument('--clear-cache', action='store_true', help='Clear session cache')
    
    args = parser.parse_args()
    
    # Clear cache
    if args.clear_cache:
        from core import clear_session_cache
        clear_session_cache()
        print("Session cache cleared")
        return
    
    # List lines
    if args.lines:
        api = OasthAPI()
        lines = api.get_lines()
        for line in lines[:20]:  # First 20
            print(f"{line.line_id}: {line.line_descr}")
        if len(lines) > 20:
            print(f"... and {len(lines) - 20} more")
        return
    
    # Get arrivals
    if not args.stop:
        parser.error("--stop is required")
    
    arrivals = get_arrivals(args.stop)
    
    # Format output
    if args.format == 'json':
        print(format_json(arrivals))
    elif args.format == 'plain':
        print(format_plain(arrivals))
    else:
        print(format_ansi(arrivals, args.stop, args.stop_name))


if __name__ == "__main__":
    main()
