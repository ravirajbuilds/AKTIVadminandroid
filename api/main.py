"""REST API for AKTIV Admin Android app ↔ AKTIV."""
from __future__ import annotations

import hmac
import logging
from datetime import date
from typing import Optional

from fastapi import FastAPI, HTTPException, Request
from fastapi.middleware.cors import CORSMiddleware
from fastapi.responses import JSONResponse
from pydantic import BaseModel, Field

from auth import authenticate
from aktiv_booking import (
    DEFAULT_COLL_CENTRE_KEY,
    cancel_booking,
    list_collection_centres,
    list_reception_users,
    next_bill_number_live,
    push_booking,
    resolve_is_test,
    search_doctors,
    search_tests,
)
from config import aktiv_settings, api_key

log = logging.getLogger("aktiv_api")

app = FastAPI(title="AKTIV Admin API", version="1.2.0")
app.add_middleware(
    CORSMiddleware,
    allow_origins=["*"],
    allow_methods=["*"],
    allow_headers=["*"],
)

_API_KEY_WARNED = False


@app.middleware("http")
async def enforce_api_key(request: Request, call_next):
    """
    Require the shared secret on data endpoints when AKTIV_API_KEY is set.
    Without it configured the API stays open (LAN-only) but logs a warning once,
    so an unprotected deployment is visible in the logs.
    """
    path = request.url.path
    protected = path.startswith("/api/")
    if protected and request.method != "OPTIONS":
        expected = api_key()
        if expected:
            provided = request.headers.get("x-api-key", "")
            # Compare as bytes: hmac.compare_digest rejects non-ASCII str inputs,
            # so a crafted header must not be able to raise instead of 401.
            if not hmac.compare_digest(provided.encode("utf-8"), expected.encode("utf-8")):
                return JSONResponse(
                    status_code=401,
                    content={"detail": "Invalid or missing API key"},
                )
        else:
            global _API_KEY_WARNED
            if not _API_KEY_WARNED:
                _API_KEY_WARNED = True
                log.warning(
                    "AKTIV_API_KEY is not set — /api endpoints are unauthenticated. "
                    "Set AKTIV_API_KEY (and rebuild the app with a matching key) to secure them."
                )
    return await call_next(request)


class BookingRequest(BaseModel):
    patient_name: str = Field(..., min_length=1)
    phone: str = Field(..., min_length=10)
    sex: str = "MALE"
    age_year: Optional[int] = None
    age_month: Optional[int] = None
    age_day: Optional[int] = None
    refrdoctor_key: Optional[int] = None
    collcentre_key: int = DEFAULT_COLL_CENTRE_KEY
    test_keys: list[int] = Field(..., min_length=1)
    bill_date: Optional[date] = None
    bill_number: Optional[str] = None
    amount_paid: Optional[float] = None
    receipt_mode: str = "CASH"
    cheque_no: Optional[str] = None
    remarks: Optional[str] = None
    test_mode: Optional[bool] = None
    sys_user_key: Optional[int] = None


class CancelRequest(BaseModel):
    sys_user_key: Optional[int] = None


class LoginRequest(BaseModel):
    userid: str = Field(..., min_length=1)
    password: str = Field(..., min_length=1)


@app.post("/api/auth/login")
def api_login(body: LoginRequest):
    try:
        user = authenticate(body.userid, body.password)
    except ValueError as exc:
        raise HTTPException(status_code=401, detail=str(exc)) from exc
    except Exception as exc:
        raise HTTPException(status_code=500, detail=str(exc)) from exc

    return {
        "success": True,
        "user_key": user.user_key,
        "userid": user.userid,
        "username": user.username,
    }


@app.get("/health")
def health():
    settings = aktiv_settings()
    return {
        "status": "ok",
        "allow_live_bookings": settings["allow_live_bookings"],
        "test_bill_date": settings["test_bill_date"].isoformat(),
        "default_sys_user_key": settings["sys_user_key"],
    }


@app.get("/api/users")
def api_users():
    """Receptionist logins — bills are stamped with sys_insert_user_key."""
    return list_reception_users()


@app.get("/api/tests")
def api_tests(q: str = "", limit: int = 50):
    return search_tests(q, limit=min(limit, 100))


@app.get("/api/doctors")
def api_doctors(q: str = "", limit: int = 50):
    return search_doctors(q, limit=min(limit, 100))


@app.get("/api/collection-centres")
def api_collection_centres(q: str = ""):
    return list_collection_centres(q)


@app.get("/api/next-bill-number")
def api_next_bill_number(bill_date: Optional[date] = None, test_mode: Optional[bool] = None):
    settings = aktiv_settings()
    is_test = resolve_is_test(test_mode, settings["allow_live_bookings"])
    effective_date = settings["test_bill_date"] if is_test else (bill_date or date.today())
    return next_bill_number_live(effective_date)


@app.post("/api/bookings")
def api_create_booking(body: BookingRequest):
    try:
        result = push_booking(
            patient_name=body.patient_name,
            phone=body.phone,
            sex=body.sex,
            age_year=body.age_year,
            age_month=body.age_month,
            age_day=body.age_day,
            refrdoctor_key=body.refrdoctor_key,
            collcentre_key=body.collcentre_key,
            test_keys=body.test_keys,
            bill_date=body.bill_date,
            bill_number=body.bill_number,
            amount_paid=body.amount_paid,
            receipt_mode=body.receipt_mode,
            cheque_no=body.cheque_no,
            remarks=body.remarks,
            test_mode=body.test_mode,
            sys_user_key=body.sys_user_key,
        )
    except ValueError as exc:
        raise HTTPException(status_code=400, detail=str(exc)) from exc
    except Exception as exc:
        raise HTTPException(status_code=500, detail=str(exc)) from exc

    settings = aktiv_settings()
    is_test = resolve_is_test(body.test_mode, settings["allow_live_bookings"])
    return {
        "success": True,
        "bill_key": result.bill_key,
        "bill_no": result.bill_no,
        "bill_number": result.bill_number,
        "registration_no": result.registration_no,
        "apnt_key": result.apnt_key,
        "net_amount": result.net_amount,
        "apnt_date": date.today().isoformat(),
        "test_mode": is_test,
    }


@app.post("/api/bookings/{bill_key}/cancel")
def api_cancel_booking(bill_key: int, body: CancelRequest = CancelRequest()):
    """
    Void receipt amounts only — never deletes bill rows (preserves ALC serials).
    """
    try:
        return cancel_booking(bill_key, sys_user_key=body.sys_user_key)
    except ValueError as exc:
        raise HTTPException(status_code=404, detail=str(exc)) from exc
    except Exception as exc:
        raise HTTPException(status_code=500, detail=str(exc)) from exc
