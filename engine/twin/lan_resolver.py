"""Smart LAN IP resolver for cross-device QR pairing.

Filters out loopback, link-local, and WSL/Docker/Hyper-V/VPN virtual subnets,
using a non-transmitting UDP socket connect to determine the true outbound interface.
"""

from __future__ import annotations

import socket
from typing import List, Tuple


def is_virtual_or_unreachable_ip(ip: str) -> bool:
    """Checks whether an IPv4 address belongs to loopback, link-local, or virtual adapter subnets."""
    if not ip or ip == "0.0.0.0" or ip.startswith("127."):
        return True
    if ip.startswith("169.254."):
        # Link-local / APIPA
        return True
    # Docker Desktop on Windows & macOS: 10.255.x.x, 192.168.65.x
    if ip.startswith("192.168.65.") or ip.startswith("10.255."):
        return True
    # WSL2 and Hyper-V Default Switches:
    # Frequently assigned in 172.17.x.x (Docker default bridge) through 172.31.x.x.
    # On consumer Wi-Fi LANs, routers use 192.168.x.x or 10.0.x.x.
    # 172.17 - 172.31 on Windows/macOS developer machines are almost exclusively WSL/Docker/Hyper-V virtual adapters.
    parts = ip.split(".")
    if len(parts) == 4 and parts[0] == "172":
        try:
            second_octet = int(parts[1])
            if 17 <= second_octet <= 31:
                return True
        except ValueError:
            pass
    return False


def get_reachable_lan_ips() -> Tuple[str, List[str]]:
    """Determines primary outbound LAN IP and candidate reachable interfaces.

    Returns:
        (primary_ip, candidate_hosts)
    """
    candidates: List[str] = []
    primary = "127.0.0.1"

    # 1. Determine primary outbound routing interface via non-transmitting UDP connect
    try:
        s = socket.socket(socket.AF_INET, socket.SOCK_DGRAM)
        # Connect to a public IP (Google DNS); UDP connect does not transmit packets
        s.connect(("8.8.8.8", 80))
        resolved_primary = s.getsockname()[0]
        s.close()
        if not is_virtual_or_unreachable_ip(resolved_primary):
            primary = resolved_primary
            candidates.append(primary)
    except Exception:
        pass

    # 2. Enumerate host interface addresses to collect secondary viable interfaces
    try:
        hostname = socket.gethostname()
        for info in socket.getaddrinfo(hostname, None, socket.AF_INET):
            addr = info[4][0]
            if not is_virtual_or_unreachable_ip(addr) and addr not in candidates:
                candidates.append(addr)
    except Exception:
        pass

    # If no candidate was found from outbound route, use first candidate or fallback
    if primary == "127.0.0.1" and candidates:
        primary = candidates[0]
    elif primary == "127.0.0.1" and not candidates:
        primary = "127.0.0.1"
        candidates = ["127.0.0.1"]

    # Always ensure loopback is appended at the end of candidates for USB / adb reverse fallback
    if "127.0.0.1" not in candidates:
        candidates.append("127.0.0.1")

    return primary, candidates
