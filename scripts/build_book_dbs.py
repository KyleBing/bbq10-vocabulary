#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""Build compact SQLite wordbooks for App assets / download page."""
from __future__ import print_function

import json
import os
import sqlite3
import sys

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
SRC_DIR = os.path.join(
    os.path.dirname(ROOT),
    "english-vocabulary",
    "full_line_jsonl",
    "sentence",
    "正序",
)

# Packaged into App assets (keep small).
BUILTIN_BOOKS = [
    ("初中.jsonl", "chuzhong.db", "初中"),
    ("高中.jsonl", "gaozhong.db", "高中"),
    ("四级.jsonl", "cet4.db", "四级"),
    ("六级.jsonl", "cet6.db", "六级"),
    ("考研.jsonl", "kaoyan.db", "考研"),
]

# Site-only downloads → App「从文件导入」. Not shipped in APK.
# Easy → hard (site download order).
DOWNLOAD_BOOKS = [
    ("专四.jsonl", "tem4.db", "专四"),
    ("专八.jsonl", "tem8.db", "专八"),
    ("雅思.jsonl", "ielts.db", "雅思"),
    ("托福.jsonl", "toefl.db", "托福"),
    ("SAT.jsonl", "sat.db", "SAT"),
    ("GMAT.jsonl", "gmat.db", "GMAT"),
    ("GRE.jsonl", "gre.db", "GRE"),
]

BOOKS = BUILTIN_BOOKS  # backward-compatible alias


def compact_trans(translations):
    parts = []
    if not translations:
        return ""
    for item in translations:
        t = (item.get("translation") or "").strip()
        pos = (item.get("type") or "").strip()
        if not t:
            continue
        if pos:
            parts.append("%s. %s" % (pos, t))
        else:
            parts.append(t)
    return " / ".join(parts)


def first_sentence(sentences):
    if not sentences:
        return "", ""
    s = sentences[0]
    return (s.get("sentence") or "").strip(), (s.get("translation") or "").strip()


def build_one(src, out, title):
    out_dir = os.path.dirname(out)
    if out_dir and not os.path.isdir(out_dir):
        os.makedirs(out_dir)
    if os.path.exists(out):
        os.remove(out)
    conn = sqlite3.connect(out)
    cur = conn.cursor()
    cur.executescript(
        """
        CREATE TABLE words (
            id INTEGER PRIMARY KEY AUTOINCREMENT,
            word TEXT NOT NULL,
            us TEXT NOT NULL DEFAULT '',
            uk TEXT NOT NULL DEFAULT '',
            trans TEXT NOT NULL DEFAULT '',
            example_en TEXT NOT NULL DEFAULT '',
            example_cn TEXT NOT NULL DEFAULT ''
        );
        CREATE INDEX idx_words_word ON words(word);
        CREATE TABLE progress (
            word_id INTEGER PRIMARY KEY,
            status INTEGER NOT NULL DEFAULT 0,
            seen INTEGER NOT NULL DEFAULT 0,
            known_count INTEGER NOT NULL DEFAULT 0,
            unknown_count INTEGER NOT NULL DEFAULT 0,
            updated_at INTEGER NOT NULL DEFAULT 0,
            box INTEGER NOT NULL DEFAULT 0,
            due_at INTEGER NOT NULL DEFAULT 0
        );
        CREATE TABLE meta (
            key TEXT PRIMARY KEY,
            value TEXT NOT NULL DEFAULT ''
        );
        """
    )
    cur.execute("INSERT INTO meta(key, value) VALUES('book_title', ?)", (title,))
    # Source JSONL often repeats the same headword; keep the richest gloss/example.
    best = {}
    with open(src, "r") as f:
        for line in f:
            line = line.strip()
            if not line:
                continue
            row = json.loads(line)
            word = (row.get("word") or "").strip()
            if not word:
                continue
            example_en, example_cn = first_sentence(row.get("sentences"))
            entry = (
                word,
                (row.get("us") or "").strip(),
                (row.get("uk") or "").strip(),
                compact_trans(row.get("translations")),
                example_en,
                example_cn,
            )
            score = len(entry[3]) + len(entry[4]) + len(entry[5])
            prev = best.get(word)
            if prev is None or score > prev[0]:
                best[word] = (score, entry)
    for word in sorted(best.keys()):
        cur.execute(
            "INSERT INTO words(word, us, uk, trans, example_en, example_cn) "
            "VALUES(?,?,?,?,?,?)",
            best[word][1],
        )
    count = len(best)
    conn.commit()
    conn.close()
    size = os.path.getsize(out)
    print("wrote %d unique words -> %s (%.2f MB) [%s]" % (count, out, size / 1024.0 / 1024.0, title))
    return count, size


def build_list(books, out_dir):
    rows = []
    for src_name, out_name, title in books:
        src = os.path.join(SRC_DIR, src_name)
        if not os.path.isfile(src):
            sys.stderr.write("skip missing %s\n" % src)
            continue
        out = os.path.join(out_dir, out_name)
        count, size = build_one(src, out, title)
        rows.append((out_name, title, count, size))
    return rows


def main():
    mode = sys.argv[1] if len(sys.argv) > 1 else "all"

    if mode in ("all", "builtin", "assets"):
        assets_books = os.path.join(ROOT, "app", "src", "main", "assets", "books")
        build_list(BUILTIN_BOOKS, assets_books)
        # legacy root alias used by first-run DbHelper.ASSET_DB
        src4 = os.path.join(SRC_DIR, "四级.jsonl")
        assets = os.path.join(ROOT, "app", "src", "main", "assets", "cet4.db")
        if os.path.isfile(src4):
            build_one(src4, assets, "四级")

    if mode in ("all", "download", "site"):
        web_books = os.path.join(ROOT, "web", "wordbooks")
        build_list(DOWNLOAD_BOOKS, web_books)
        dist_books = os.path.join(ROOT, "dist", "wordbooks")
        build_list(DOWNLOAD_BOOKS, dist_books)


if __name__ == "__main__":
    main()
