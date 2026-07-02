"""Load shared env from repo root (.env or env)."""
from __future__ import annotations

import os
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent


def load_env() -> None:
    for name in (".env", "env"):
        path = ROOT / name
        if not path.exists():
            continue
        for line in path.read_text().splitlines():
            line = line.strip()
            if not line or line.startswith("#") or "=" not in line:
                continue
            key, value = line.split("=", 1)
            os.environ.setdefault(key.strip(), value.strip().strip('"'))


def mssql_config() -> dict:
    load_env()
    return {
        "server": os.environ["MSSQL_HOST"],
        "port": int(os.environ.get("MSSQL_PORT", "1433")),
        "user": os.environ["MSSQL_USER"],
        "password": os.environ["MSSQL_PASSWORD"],
        "database": os.environ["MSSQL_DB"],
        "tds_version": os.environ.get("MSSQL_TDS_VERSION", "7.0"),
    }


def neon_url() -> str:
    load_env()
    return os.environ["NEON_DATABASE_URL"]
