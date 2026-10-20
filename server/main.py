"""Inventory sync server for PhoneShopInventory.

Run:  API_TOKEN=<long-random-secret> uvicorn main:app --host 127.0.0.1 --port 8000
Put it behind HTTPS (Caddy/nginx) — the Android app only talks to https:// URLs.

Sync model: every product has a client-generated uuid and an updatedAt (ms).
The server keeps the newest version of each product (last-write-wins) and gives
every accepted change a global increasing `seq`, so each phone only downloads
what it has not seen yet. Deletes are kept as tombstones (deleted = true).
"""
import os
import secrets
import sqlite3
import threading
from contextlib import closing
from typing import List

from fastapi import Depends, FastAPI, Header, HTTPException
from pydantic import BaseModel, Field

DB_PATH = os.environ.get("DB_PATH", "inventory.db")
API_TOKEN = os.environ.get("API_TOKEN", "")
if len(API_TOKEN) < 16:
    raise RuntimeError("Set API_TOKEN to a random secret of at least 16 characters")

_lock = threading.Lock()

SCHEMA = """
CREATE TABLE IF NOT EXISTS products (
    uuid       TEXT PRIMARY KEY,
    category   TEXT NOT NULL,
    brand      TEXT NOT NULL,
    model      TEXT NOT NULL,
    color      TEXT NOT NULL,
    storage    TEXT NOT NULL,
    imei       TEXT NOT NULL,
    buy_price  INTEGER NOT NULL,
    sell_price INTEGER NOT NULL,
    quantity   INTEGER NOT NULL,
    note       TEXT NOT NULL,
    created_at INTEGER NOT NULL,
    updated_at INTEGER NOT NULL,
    deleted    INTEGER NOT NULL,
    seq        INTEGER NOT NULL
);
CREATE INDEX IF NOT EXISTS idx_products_seq ON products(seq);
"""


def connect() -> sqlite3.Connection:
    conn = sqlite3.connect(DB_PATH, timeout=30)
    conn.row_factory = sqlite3.Row
    return conn


with closing(connect()) as _c:
    _c.executescript(SCHEMA)
    _c.commit()


class Product(BaseModel):
    uuid: str = Field(min_length=8, max_length=64)
    category: str = Field(default="", max_length=60)
    brand: str = Field(default="", max_length=120)
    model: str = Field(default="", max_length=120)
    color: str = Field(default="", max_length=60)
    storage: str = Field(default="", max_length=60)
    imei: str = Field(default="", max_length=20)
    buyPrice: int = Field(default=0, ge=0, le=10**13)
    sellPrice: int = Field(default=0, ge=0, le=10**13)
    quantity: int = Field(default=0, ge=0, le=10**7)
    note: str = Field(default="", max_length=1000)
    createdAt: int = Field(ge=0)
    updatedAt: int = Field(ge=0)
    deleted: bool = False


class SyncRequest(BaseModel):
    cursor: int = Field(default=0, ge=0)
    changes: List[Product] = Field(default_factory=list, max_length=1000)


class SyncResponse(BaseModel):
    cursor: int
    products: List[Product]


def require_token(authorization: str = Header(default="")):
    prefix = "Bearer "
    supplied = authorization[len(prefix):] if authorization.startswith(prefix) else ""
    if not secrets.compare_digest(supplied.encode(), API_TOKEN.encode()):
        raise HTTPException(status_code=401, detail="invalid token")


app = FastAPI(title="PhoneShop inventory sync", docs_url=None, redoc_url=None)


@app.get("/health")
def health():
    return {"ok": True}


def row_to_product(r: sqlite3.Row) -> Product:
    return Product(
        uuid=r["uuid"], category=r["category"], brand=r["brand"], model=r["model"],
        color=r["color"], storage=r["storage"], imei=r["imei"],
        buyPrice=r["buy_price"], sellPrice=r["sell_price"], quantity=r["quantity"],
        note=r["note"], createdAt=r["created_at"], updatedAt=r["updated_at"],
        deleted=bool(r["deleted"]),
    )


@app.post("/sync", response_model=SyncResponse, dependencies=[Depends(require_token)])
def sync(req: SyncRequest):
    with _lock, closing(connect()) as conn:
        conn.execute("BEGIN IMMEDIATE")
        seq = conn.execute("SELECT COALESCE(MAX(seq), 0) FROM products").fetchone()[0]

        for p in req.changes:
            row = conn.execute(
                "SELECT updated_at FROM products WHERE uuid = ?", (p.uuid,)
            ).fetchone()
            if row is not None and p.updatedAt <= row["updated_at"]:
                continue  # server already has an equal or newer version
            seq += 1
            conn.execute(
                """INSERT INTO products (uuid, category, brand, model, color, storage, imei,
                       buy_price, sell_price, quantity, note, created_at, updated_at, deleted, seq)
                   VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?,?,?)
                   ON CONFLICT(uuid) DO UPDATE SET
                       category=excluded.category, brand=excluded.brand, model=excluded.model,
                       color=excluded.color, storage=excluded.storage, imei=excluded.imei,
                       buy_price=excluded.buy_price, sell_price=excluded.sell_price,
                       quantity=excluded.quantity, note=excluded.note,
                       created_at=excluded.created_at, updated_at=excluded.updated_at,
                       deleted=excluded.deleted, seq=excluded.seq""",
                (p.uuid, p.category, p.brand, p.model, p.color, p.storage, p.imei,
                 p.buyPrice, p.sellPrice, p.quantity, p.note, p.createdAt, p.updatedAt,
                 int(p.deleted), seq),
            )

        rows = conn.execute(
            "SELECT * FROM products WHERE seq > ? ORDER BY seq", (req.cursor,)
        ).fetchall()
        conn.commit()

    new_cursor = max([req.cursor] + [r["seq"] for r in rows])
    return SyncResponse(cursor=new_cursor, products=[row_to_product(r) for r in rows])
