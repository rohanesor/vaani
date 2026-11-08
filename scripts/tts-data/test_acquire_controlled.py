#!/usr/bin/env python3
import importlib.util
import json
import os
import tempfile
import unittest
from pathlib import Path

SPEC = importlib.util.spec_from_file_location("acquire_controlled", Path(__file__).with_name("acquire_controlled.py"))
MOD = importlib.util.module_from_spec(SPEC)
SPEC.loader.exec_module(MOD)

class AcquisitionTests(unittest.TestCase):
    def test_language_allowlist(self):
        self.assertEqual(MOD.parse_csv("hi,gu,bn", set(MOD.LANGUAGES), "languages"), ["hi", "gu", "bn"])
        with self.assertRaises(ValueError): MOD.parse_csv("en", set(MOD.LANGUAGES), "languages")

    def test_budget_calculation(self):
        self.assertEqual(MOD.bytes_for_hours(10), 3456000000)
        self.assertEqual(MOD.bytes_for_hours(10, 22050), 1587600000)

    def test_deterministic_identity_validation_and_duplicates(self):
        row = {"source_file":"Gujarati/train/0000.parquet", "source_record_id":"0", "language":"gu", "split":"train", "speaker_id":"s1", "gender":"Female", "duration":2.0, "scenario":"Read", "task_name":"x", "original_text":"મદદ", "normalized_text":"મદદ"}
        self.assertEqual(len(MOD.validate_records([row], {"gu"}, {"train"})), 1)
        with self.assertRaises(ValueError): MOD.validate_records([row, dict(row)], {"gu"}, {"train"})

    def test_deterministic_selection_order(self):
        rows = [{"id": x} for x in range(20)]
        self.assertEqual(MOD.deterministic_order(rows, 7), MOD.deterministic_order(rows, 7))
        self.assertNotEqual(MOD.deterministic_order(rows, 7), MOD.deterministic_order(rows, 8))

    def test_speaker_disjoint_splitting(self):
        rows = [{"split":"train", "language":"gu", "speaker_id":"a"}, {"split":"validation", "language":"gu", "speaker_id":"b"}]
        self.assertTrue(MOD.speaker_disjoint(rows))
        rows.append({"split":"test", "language":"gu", "speaker_id":"a"})
        self.assertFalse(MOD.speaker_disjoint(rows))

    def test_provenance_completeness(self):
        row = {"source_file":"x", "source_record_id":"0", "language":"gu", "split":"train", "speaker_id":"s", "gender":"u", "duration":1, "scenario":"s", "task_name":"t", "original_text":"x", "normalized_text":"x"}
        self.assertFalse(MOD.REQUIRED_RECORD_FIELDS - set(row))

    def test_no_token_dry_run_refuses(self):
        old = os.environ.pop("HF_TOKEN", None)
        try:
            with self.assertRaises(SystemExit):
                MOD.main(["--output", "/tmp/none", "--manifest", "/tmp/none.json", "--no-training", "--dry-run"])
        finally:
            if old is not None: os.environ["HF_TOKEN"] = old

    def test_no_training_is_required_by_cli(self):
        with self.assertRaises(SystemExit): MOD.build_parser().parse_args(["--output", "/tmp/x", "--manifest", "/tmp/m", "--dry-run"])

    def test_approval_requires_byte_budget_and_manifest(self):
        old = os.environ.get("HF_TOKEN")
        os.environ["HF_TOKEN"] = "placeholder-not-printed"
        try:
            with self.assertRaises(SystemExit):
                MOD.main(["--output", "/tmp", "--manifest", "/tmp/missing", "--no-training", "--approve-acquisition"])
        finally:
            if old is None: os.environ.pop("HF_TOKEN", None)
            else: os.environ["HF_TOKEN"] = old

if __name__ == "__main__": unittest.main()
