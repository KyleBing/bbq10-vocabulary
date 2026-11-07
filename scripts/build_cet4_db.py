#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""Convert CET-4 sentence JSONL into a compact SQLite dictionary."""
from __future__ import print_function

import json
import os
import sqlite3
import sys

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
DEFAULT_SRC = os.path.join(
    os.path.dirname(ROOT),
    "english-vocabulary",
    "full_line_jsonl",
    "sentence",
    "正序",
    "四级.jsonl",
)
DEFAULT_OUT = os.path.join(ROOT, "app", "src", "main", "assets", "cet4.db")


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


def main():
    src = sys.argv[1] if len(sys.argv) > 1 else DEFAULT_SRC
    out = sys.argv[2] if len(sys.argv) > 2 else DEFAULT_OUT
    if not os.path.isfile(src):
        sys.stderr.write("missing source: %s\n" % src)
        sys.exit(1)

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
        INSERT INTO meta(key, value) VALUES('book_title', '四级');
        """
    )

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
    print("wrote %d unique words -> %s (%.2f MB)" % (count, out, size / 1024.0 / 1024.0))


if __name__ == "__main__":
    main()
