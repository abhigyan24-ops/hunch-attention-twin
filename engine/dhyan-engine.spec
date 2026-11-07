# -*- mode: python ; coding: utf-8 -*-
from pathlib import Path
import sys

block_cipher = None

engine_dir = Path(__file__).resolve().parent

datas = [
    (str(engine_dir / "twin" / "ingest" / "schema.sql"), "twin/ingest"),
]

hiddenimports = [
    "uvicorn",
    "uvicorn.logging",
    "uvicorn.loops",
    "uvicorn.loops.auto",
    "uvicorn.protocols",
    "uvicorn.protocols.http",
    "uvicorn.protocols.http.auto",
    "uvicorn.protocols.websockets",
    "uvicorn.protocols.websockets.auto",
    "uvicorn.lifespans",
    "uvicorn.lifespans.on",
    "uvicorn.lifespans.auto",
    "fastapi",
    "pydantic",
    "sqlite3",
    "sklearn",
    "sklearn.ensemble",
    "sklearn.utils._typedefs",
    "lightgbm",
    "lifelines",
    "econml",
    "statsmodels",
    "dowhy",
    "hmmlearn",
]

a = Analysis(
    ["run_engine.py"],
    pathex=[str(engine_dir)],
    binaries=[],
    datas=datas,
    hiddenimports=hiddenimports,
    hookspath=[],
    hooksconfig={},
    runtime_hooks=[],
    excludes=["tkinter", "matplotlib"],
    win_no_prefer_redirects=False,
    win_private_assemblies=False,
    cipher=block_cipher,
    noarchive=False,
)

pyz = PYZ(a.pure, a.zipped_data, cipher=block_cipher)

exe = EXE(
    pyz,
    a.scripts,
    [],
    exclude_binaries=True,
    name="dhyan-engine",
    debug=False,
    bootloader_ignore_signals=False,
    strip=False,
    upx=True,
    console=True,
    disable_windowed_traceback=False,
    argv_emulation=False,
    target_arch=None,
    codesign_identity=None,
    entitlements_file=None,
)

coll = COLLECT(
    exe,
    a.binaries,
    a.zipfiles,
    a.datas,
    strip=False,
    upx=True,
    upx_exclude=[],
    name="dhyan-engine",
)
