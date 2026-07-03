"""AKTIV desktop login — validates against SYS_MAST_USERS."""
from __future__ import annotations

from dataclasses import dataclass

from config import admin_userids
from db import mssql_conn


@dataclass
class AuthUser:
    user_key: int
    userid: str
    username: str | None
    is_admin: bool = False


def _is_admin(userid: str | None) -> bool:
    admins = admin_userids()
    return bool(admins) and (userid or "").strip().upper() in admins


def user_is_admin(user_key: int) -> bool:
    """Resolve a user_key back to its login and check admin rights (server-side guard)."""
    with mssql_conn() as conn, conn.cursor() as cur:
        cur.execute(
            "SELECT userid FROM SYS_MAST_USERS WHERE user_key = %s",
            (user_key,),
        )
        row = cur.fetchone()
    if not row:
        return False
    return _is_admin(str(row[0]) if row[0] is not None else None)


def authenticate(userid: str, password: str) -> AuthUser:
    """Match AKTIV login: USERID + USERPASSWORD from SYS_MAST_USERS."""
    login_id = userid.strip()
    if not login_id or not password:
        raise ValueError("Username and password are required")

    with mssql_conn() as conn, conn.cursor() as cur:
        cur.execute(
            """
            SELECT user_key, userid, username, userpassword
            FROM SYS_MAST_USERS
            WHERE UPPER(userid) = UPPER(%s)
            """,
            (login_id,),
        )
        row = cur.fetchone()

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
