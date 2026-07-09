"""AKTIV desktop login — validates against SYS_MAST_USERS."""
from __future__ import annotations

from dataclasses import dataclass, field

from db import mssql_conn
from roles import load_user_permissions


@dataclass
class AuthUser:
    user_key: int
    userid: str
    username: str | None
    role: str = "staff"
    permissions: dict = field(default_factory=dict)


def _primary_role(perms: dict) -> str:
    if perms.get("is_admin"):
        return "admin"
    if perms.get("can_view_sales"):
        return "account"
    if perms.get("can_book"):
        return "reception"
    return "staff"


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

    perms = load_user_permissions(int(user_key))
    return AuthUser(
        user_key=int(user_key),
        userid=str(db_userid or login_id),
        username=str(username) if username else str(db_userid or login_id),
        role=_primary_role(perms),
        permissions=perms,
    )
