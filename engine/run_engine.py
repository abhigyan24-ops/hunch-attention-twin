# engine/run_engine.py
import os
import sys
from pathlib import Path

engine_root = Path(__file__).resolve().parent
if str(engine_root) not in sys.path:
    sys.path.insert(0, str(engine_root))

if __name__ == "__main__":
    port = int(os.getenv("DHYAN_ENGINE_PORT", "8765"))
    host = os.getenv("DHYAN_ENGINE_HOST", "127.0.0.1")
    from twin.api.app import app
    import uvicorn

    uvicorn.run(app, host=host, port=port, log_level="info", access_log=False)
