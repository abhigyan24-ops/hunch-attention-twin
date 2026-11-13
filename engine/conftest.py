import sys
from pathlib import Path

# Ensure both engine/ and workspace root are in sys.path
ENGINE_DIR = Path(__file__).resolve().parent
WORKSPACE_DIR = ENGINE_DIR.parent

for p in (str(ENGINE_DIR), str(WORKSPACE_DIR)):
    if p not in sys.path:
        sys.path.insert(0, p)
