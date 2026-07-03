"""
AKTIV desktop login — validates against SYS_MAST_USERS.

Reads the on-LAN AKTIV SQL Server first; if it is unreachable (the host is not
on the AKTIV network), it falls back to the Neon mirror, which the ETL refreshes
every 15 min during working hours. Neon is read-only, so login and admin checks
still work off-LAN even though bookings can only be written on-LAN.
"""
from __future__ import annotations

import logging
from dataclasses import dataclass

from config import admin_userids
from db import mssql_conn, neon_conn

log = logging.getLogger("auth")


@dataclass
class AuthUser:
    user_key: int
    userid: str
    username: str | None
    is_admin: bool = False


def _is_admin(userid: str | None) -> bool:
    admins = admin_userids()
    return bool(admins) and (userid or "").strip().upper() in admins


def _query_first(mssql_sql: str, neon_sql: str, params: tuple):
    """
    Run a lookup against AKTIV MSSQL, falling back to the Neon mirror if MSSQL is
    unreachable. A missing row (fetchone() -> None) is a normal result, not a
    failure, so it never triggers the fallback.
    """
    try:
        with mssql_conn() as conn, conn.cursor() as cur:
            cur.execute(mssql_sql, params)
            return cur.fetchone()
    except Exception as primary:  # MSSQL down / off-LAN — try the Neon mirror.
        log.warning("AKTIV MSSQL unavailable, falling back to Neon: %s", primary)
        try:
            with neon_conn() as conn, conn.cursor() as cur:
                cur.execute(neon_sql, params)
                return cur.fetchone()
        except Exception as fallback:
            raise RuntimeError(
                f"AKTIV (MSSQL) and Neon lookups both failed: {primary}; {fallback}"
            ) from fallback


def user_is_admin(user_key: int) -> bool:
    """Resolve a user_key back to its login and check admin rights (server-side guard)."""
    row = _query_first(
        "SELECT userid FROM SYS_MAST_USERS WHERE user_key = %s",
        "SELECT userid FROM sys_mast_users WHERE user_key = %s",
        (user_key,),
    )
    if not row:
        return False
    return _is_admin(str(row[0]) if row[0] is not None else None)


def authenticate(userid: str, password: str) -> AuthUser:
    """Match AKTIV login: USERID + USERPASSWORD from SYS_MAST_USERS (MSSQL or Neon)."""
    login_id = userid.strip()
    if not login_id or not password:
        raise ValueError("Username and password are required")

    row = _query_first(
        "SELECT user_key, userid, username, userpassword "
        "FROM SYS_MAST_USERS WHERE UPPER(userid) = UPPER(%s)",
        "SELECT user_key, userid, username, userpassword "
        "FROM sys_mast_users WHERE UPPER(userid) = UPPER(%s)",
        (login_id,),
    )

    if not row:
        raise ValueError("Invalid username or password")

    user_key, db_userid, username, db_password = row
    stored = "" if db_password is None else str(db_password)
    if stored != password:
        raise ValueError("Invalid username or password")

    resolved_userid = str(db_userid or login_id)
    return AuthUser(
        user_key=int(user_key),
        userid=resolved_userid,
        username=str(username) if username else resolved_userid,
        is_admin=_is_admin(resolved_userid),
    )
