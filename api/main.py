"""REST API for Anubhav Life Care Android app ↔ AKTIV."""
from __future__ import annotations

from datetime import date
from typing import Optional

from fastapi import FastAPI, HTTPException
from fastapi.middleware.cors import CORSMiddleware
from pydantic import BaseModel, Field

from aktiv_booking import (
    DEFAULT_COLL_CENTRE_KEY,
    list_collection_centres,
    next_bill_number,
    push_booking,
    search_doctors,
    search_tests,
)

app = FastAPI(title="Anubhav AKTIV API", version="1.0.0")
app.add_middleware(
    CORSMiddleware,
    allow_origins=["*"],
    allow_methods=["*"],
    allow_headers=["*"],
)


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


@app.get("/health")
def health():
    return {"status": "ok"}


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
def api_next_bill_number(bill_date: Optional[date] = None):
    return next_bill_number(bill_date)


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
        )
    except ValueError as exc:
        raise HTTPException(status_code=400, detail=str(exc)) from exc
    except Exception as exc:
        raise HTTPException(status_code=500, detail=str(exc)) from exc

    return {
        "success": True,
        "bill_key": result.bill_key,
        "bill_no": result.bill_no,
        "bill_number": result.bill_number,
        "registration_no": result.registration_no,
        "apnt_key": result.apnt_key,
        "net_amount": result.net_amount,
        "apnt_date": date.today().isoformat(),
    }
