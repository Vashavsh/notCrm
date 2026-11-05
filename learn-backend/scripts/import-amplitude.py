#!/usr/bin/env python3
"""Reads the "Доступ к Amplitude.docx" table and writes one <resource id>.json per Amplitude dataset
into the secrets directory (LEARN_SECRETS_DIR), outside the repository.

    python3 -I scripts/import-amplitude.py <docx> <content dir> <secrets dir>

The document has a table with the columns: data period, organization, login, password, chapter.
Each row is matched to a resource in <content dir>/resources.json by its "sourcePeriod". Logins and
passwords are never printed; only the ids of the resources that were written. Run it again to replace
the logins, for example after the datasets move to the school's own Amplitude account.
"""
import json
import os
import re
import sys
import zipfile
from xml.etree import ElementTree

NS = {"w": "http://schemas.openxmlformats.org/wordprocessingml/2006/main"}
LOGIN_URL = "https://analytics.amplitude.com/login"


def fail(message):
    sys.exit("error: " + message)


def normalize(period):
    return re.sub(r"\s|[–—-]", "", period).lower()


def read_rows(docx):
    try:
        with zipfile.ZipFile(docx) as archive:
            root = ElementTree.fromstring(archive.read("word/document.xml"))
    except (OSError, zipfile.BadZipFile, KeyError, ElementTree.ParseError):
        fail("cannot read the DOCX")
    rows = []
    for table in root.findall(".//w:tbl", NS):
        for row in table.findall("./w:tr", NS):
            cells = ["".join(t.text or "" for t in cell.findall(".//w:t", NS)).strip()
                     for cell in row.findall("./w:tc", NS)]
            if len(cells) >= 5 and re.search(r"2016", cells[0]) and all(cells[i] for i in (1, 2, 3)):
                rows.append(cells)
    return rows


def main():
    if len(sys.argv) != 4:
        fail("usage: import-amplitude.py <docx> <content dir> <secrets dir>")
    docx, content_dir, secrets_dir = sys.argv[1:]
    with open(os.path.join(content_dir, "resources.json"), encoding="utf-8-sig") as f:
        catalog = {normalize(item["sourcePeriod"]): item for item in json.load(f) if "sourcePeriod" in item}

    rows = read_rows(docx)
    if not rows:
        fail("the document has no filled-in rows")
    written = {}
    for cells in rows:
        key = normalize(cells[0])
        if key not in catalog:
            fail("a period in the document is not in resources.json: " + cells[0])
        item = catalog[key]
        if item["id"] in written:
            fail("a data period appears twice in the document: " + cells[0])
        written[item["id"]] = {
            "title": item["title"], "url": LOGIN_URL, "organization": cells[1], "login": cells[2],
            "password": cells[3], "dateRange": cells[0], "chapter": cells[4],
        }

    os.makedirs(secrets_dir, mode=0o700, exist_ok=True)
    for resource_id, data in written.items():
        path = os.path.join(secrets_dir, resource_id + ".json")
        fd = os.open(path, os.O_WRONLY | os.O_CREAT | os.O_TRUNC, 0o600)
        with os.fdopen(fd, "w", encoding="utf-8") as f:
            json.dump(data, f, ensure_ascii=False, indent=2)
    missing = [item["id"] for item in catalog.values() if item["id"] not in written]
    print("written (%d): %s" % (len(written), ", ".join(sorted(written))))
    if missing:
        print("not in the document (no logins yet): " + ", ".join(sorted(missing)))


if __name__ == "__main__":
    main()
