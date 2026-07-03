"""
Unit tests for the pure billing helpers.

Run from the repo root or api/ dir:
    python -m unittest api/test_aktiv_booking.py -v

The DB drivers (psycopg2/pymssql) are stubbed so these run without a database or
FreeTDS installed — the functions under test do no I/O.
"""
import os
import sys
import types
import unittest
from datetime import date, datetime
from pathlib import Path

API_DIR = Path(__file__).resolve().parent
sys.path.insert(0, str(API_DIR))

for _name in ("psycopg2", "pymssql"):
    if _name not in sys.modules:
        sys.modules[_name] = types.ModuleType(_name)

import aktiv_booking as ab  # noqa: E402
import config  # noqa: E402


class BillHelpersTest(unittest.TestCase):
    def test_bill_prefix1(self):
        self.assertEqual(ab._bill_prefix1(date(2025, 7, 2)), "2025/07")
        self.assertEqual(ab._bill_prefix1(date(2025, 12, 31)), "2025/12")
        self.assertEqual(ab._bill_prefix1(date(2026, 1, 5)), "2026/01")

    def test_year_prefix(self):
        self.assertEqual(ab._year_prefix(date(2025, 7, 2)), "25")
        self.assertEqual(ab._year_prefix(date(2009, 1, 1)), "09")
        self.assertEqual(ab._year_prefix(date(2000, 6, 6)), "00")

    def test_decimal_time(self):
        self.assertEqual(ab._decimal_time(datetime(2025, 7, 2, 9, 30)), 9.5)
        self.assertEqual(ab._decimal_time(datetime(2025, 7, 2, 0, 0)), 0.0)
        self.assertEqual(ab._decimal_time(datetime(2025, 7, 2, 23, 15)), 23.25)

    def test_format_bill_number_zero_pads(self):
        info = ab._format_bill_number("2025/07", 1)
        self.assertEqual(info["bill_number"], "001")
        self.assertEqual(info["bill_prefix1"], "2025/07")
        self.assertEqual(info["bill_prefix2"], "ALC")
        self.assertEqual(info["bill_no"], "2025/07/ALC/001")

    def test_format_bill_number_two_and_three_digits(self):
        self.assertEqual(ab._format_bill_number("2025/07", 42)["bill_number"], "042")
        self.assertEqual(ab._format_bill_number("2025/07", 100)["bill_number"], "100")

    def test_format_bill_number_beyond_three_digits_not_truncated(self):
        info = ab._format_bill_number("2025/07", 1234)
        self.assertEqual(info["bill_number"], "1234")
        self.assertEqual(info["bill_no"], "2025/07/ALC/1234")

    def test_sex_and_receipt_maps(self):
        self.assertEqual(ab.SEX["MALE"], 1)
        self.assertEqual(ab.SEX["FEMALE"], 2)
        self.assertEqual(ab.RECEIPT_MODE["CASH"], 1)
        self.assertEqual(ab.RECEIPT_MODE["UPI"], 3)
        self.assertEqual(ab.RECEIPT_MODE["CARD"], 6)


class ResolveIsTestTest(unittest.TestCase):
    def test_switch_off_always_forces_test(self):
        # The server safety gate wins: a client can never force a live bill.
        self.assertTrue(ab.resolve_is_test(None, allow_live=False))
        self.assertTrue(ab.resolve_is_test(True, allow_live=False))
        self.assertTrue(ab.resolve_is_test(False, allow_live=False))

    def test_switch_on_respects_client(self):
        self.assertTrue(ab.resolve_is_test(True, allow_live=True))
        self.assertFalse(ab.resolve_is_test(False, allow_live=True))

    def test_switch_on_no_client_pref_defaults_live(self):
        self.assertFalse(ab.resolve_is_test(None, allow_live=True))


class ConfigTest(unittest.TestCase):
    def test_api_key_empty_by_default(self):
        os.environ.pop("AKTIV_API_KEY", None)
        self.assertEqual(config.api_key(), "")

    def test_api_key_is_trimmed(self):
        os.environ["AKTIV_API_KEY"] = "  secret123  "
        try:
            self.assertEqual(config.api_key(), "secret123")
        finally:
            os.environ.pop("AKTIV_API_KEY", None)

    def test_settings_default_to_test_mode(self):
        os.environ.pop("AKTIV_ALLOW_LIVE_BOOKINGS", None)
        self.assertFalse(config.aktiv_settings()["allow_live_bookings"])

    def test_settings_allow_live_when_true(self):
        os.environ["AKTIV_ALLOW_LIVE_BOOKINGS"] = "true"
        try:
            self.assertTrue(config.aktiv_settings()["allow_live_bookings"])
        finally:
            os.environ.pop("AKTIV_ALLOW_LIVE_BOOKINGS", None)


if __name__ == "__main__":
    unittest.main()
