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

BOOKS = [
    ("四级.jsonl", "cet4.db", "四级"),
    ("六级.jsonl", "cet6.db", "六级"),
    ("高中.jsonl", "gaozhong.db", "高中"),
    ("初中.jsonl", "chuzhong.db", "初中"),
    ("考研.jsonl", "kaoyan.db", "考研"),
]


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
    count = 0
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
            cur.execute(
                "INSERT INTO words(word, us, uk, trans, example_en, example_cn) "
                "VALUES(?,?,?,?,?,?)",
                (
                    word,
                    (row.get("us") or "").strip(),
                    (row.get("uk") or "").strip(),
                    compact_trans(row.get("translations")),
                    example_en,
                    example_cn,
                ),
            )
            count += 1
    conn.commit()
    conn.close()
    size = os.path.getsize(out)
    print("wrote %d words -> %s (%.2f MB) [%s]" % (count, out, size / 1024.0 / 1024.0, title))
    return count, size


def main():
    out_dir = sys.argv[1] if len(sys.argv) > 1 else os.path.join(ROOT, "dist", "wordbooks")
    rows = []
    for src_name, out_name, title in BOOKS:
        src = os.path.join(SRC_DIR, src_name)
        if not os.path.isfile(src):
            sys.stderr.write("skip missing %s\n" % src)
            continue
        out = os.path.join(out_dir, out_name)
        count, size = build_one(src, out, title)
        rows.append((out_name, title, count, size))

    # keep assets cet4 in sync
    assets = os.path.join(ROOT, "app", "src", "main", "assets", "cet4.db")
    src4 = os.path.join(SRC_DIR, "四级.jsonl")
    if os.path.isfile(src4):
        build_one(src4, assets, "四级")

    return rows


if __name__ == "__main__":
    main()
