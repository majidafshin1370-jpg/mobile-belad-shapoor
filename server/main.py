"""Inventory sync server for PhoneShopInventory.

Run:  API_TOKEN=<long-random-secret> uvicorn main:app --host 127.0.0.1 --port 8000
Put it behind HTTPS (Caddy/nginx) — the Android app only talks to https:// URLs.

Sync model: every record (product, debtor, payment, sale) has a client-generated id and an
updatedAt (ms). The server keeps the newest version of each record (last-write-wins) and gives
every accepted change a global increasing `seq` (shared by all four tables), so each phone only
downloads what it has not seen yet. Deletes are kept as tombstones (deleted = true).

Old app versions only send/receive what they know; the extra lists are optional, so they keep working.

Product photos are small JPEG files in PHOTO_DIR (default: a "photos" folder next to the database),
stored as <product-uuid>_<photoAt>.jpg and sent with PUT/GET /photo/<uuid>/<photoAt>.
The change log ("history") is append-only.
"""
import os
import re
import secrets
import sqlite3
import threading
from contextlib import closing
from typing import List

from fastapi import Depends, FastAPI, Header, HTTPException, Request
from fastapi.responses import FileResponse
from pydantic import BaseModel, Field

DB_PATH = os.environ.get("DB_PATH", "inventory.db")
API_TOKEN = os.environ.get("API_TOKEN", "")
if len(API_TOKEN) < 16:
    raise RuntimeError("Set API_TOKEN to a random secret of at least 16 characters")

_lock = threading.Lock()

# IF NOT EXISTS: an existing inventory.db keeps its products and simply gains the new tables.
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
    seq        INTEGER NOT NULL,
    photo_at   INTEGER NOT NULL DEFAULT 0
);
CREATE INDEX IF NOT EXISTS idx_products_seq ON products(seq);

CREATE TABLE IF NOT EXISTS debtors (
    id                TEXT PRIMARY KEY,
    name              TEXT NOT NULL,
    phone             TEXT NOT NULL,
    amount            INTEGER NOT NULL,
    note              TEXT NOT NULL,
    created_at        INTEGER NOT NULL,
    updated_at        INTEGER NOT NULL,
    purchase_date     TEXT NOT NULL,
    due_date          TEXT NOT NULL,
    last_reminder_due TEXT NOT NULL,
    closed            INTEGER NOT NULL,
    deleted           INTEGER NOT NULL,
    seq               INTEGER NOT NULL
);
CREATE INDEX IF NOT EXISTS idx_debtors_seq ON debtors(seq);

CREATE TABLE IF NOT EXISTS payments (
    id         TEXT PRIMARY KEY,
    debtor_id  TEXT NOT NULL,
    sale_id    TEXT NOT NULL,
    amount     INTEGER NOT NULL,
    date       TEXT NOT NULL,
    method     TEXT NOT NULL,
    note       TEXT NOT NULL,
    created_at INTEGER NOT NULL,
    updated_at INTEGER NOT NULL,
    deleted    INTEGER NOT NULL,
    seq        INTEGER NOT NULL
);
CREATE INDEX IF NOT EXISTS idx_payments_seq ON payments(seq);

CREATE TABLE IF NOT EXISTS sales (
    id           TEXT PRIMARY KEY,
    product_id   TEXT NOT NULL,
    product_name TEXT NOT NULL,
    quantity     INTEGER NOT NULL,
    unit_price   INTEGER NOT NULL,
    unit_cost    INTEGER NOT NULL,
    brand        TEXT NOT NULL DEFAULT '',
    buyer_name   TEXT NOT NULL,
    buyer_phone  TEXT NOT NULL,
    date         TEXT NOT NULL,
    credit       INTEGER NOT NULL,
    debtor_id    TEXT NOT NULL,
    credit_amount INTEGER NOT NULL,
    note         TEXT NOT NULL,
    created_at   INTEGER NOT NULL,
    updated_at   INTEGER NOT NULL,
    deleted      INTEGER NOT NULL,
    seq          INTEGER NOT NULL
);
CREATE INDEX IF NOT EXISTS idx_sales_seq ON sales(seq);

CREATE TABLE IF NOT EXISTS history (
    id         TEXT PRIMARY KEY,
    at_ms      INTEGER NOT NULL,
    who        TEXT NOT NULL,
    kind       TEXT NOT NULL,
    title      TEXT NOT NULL,
    detail     TEXT NOT NULL,
    updated_at INTEGER NOT NULL,
    seq        INTEGER NOT NULL
);
CREATE INDEX IF NOT EXISTS idx_history_seq ON history(seq);
"""

TABLES = ("products", "debtors", "payments", "sales", "history")

# Columns added after the first release: older databases get them here.
NEW_COLUMNS = (
    ("products", "photo_at", "INTEGER NOT NULL DEFAULT 0"),
    ("sales", "brand", "TEXT NOT NULL DEFAULT ''"),
)


def connect() -> sqlite3.Connection:
    conn = sqlite3.connect(DB_PATH, timeout=30)
    conn.row_factory = sqlite3.Row
    return conn


with closing(connect()) as _c:
    _c.executescript(SCHEMA)
    for _table, _column, _ddl in NEW_COLUMNS:
        _have = {r["name"] for r in _c.execute(f"PRAGMA table_info({_table})")}
        if _column not in _have:
            _c.execute(f"ALTER TABLE {_table} ADD COLUMN {_column} {_ddl}")
    _c.commit()

MONEY = 10**13


class Product(BaseModel):
    uuid: str = Field(min_length=8, max_length=64)
    category: str = Field(default="", max_length=60)
    brand: str = Field(default="", max_length=120)
    model: str = Field(default="", max_length=120)
    color: str = Field(default="", max_length=60)
    storage: str = Field(default="", max_length=60)
    imei: str = Field(default="", max_length=20)
    buyPrice: int = Field(default=0, ge=0, le=MONEY)
    sellPrice: int = Field(default=0, ge=0, le=MONEY)
    quantity: int = Field(default=0, ge=0, le=10**7)
    note: str = Field(default="", max_length=1000)
    createdAt: int = Field(ge=0)
    updatedAt: int = Field(ge=0)
    deleted: bool = False
    photoAt: int = Field(default=0, ge=0)


class Debtor(BaseModel):
    id: str = Field(min_length=8, max_length=64)
    name: str = Field(default="", max_length=120)
    phone: str = Field(default="", max_length=40)
    amount: int = Field(default=0, ge=0, le=MONEY)
    note: str = Field(default="", max_length=1000)
    createdAt: int = Field(ge=0)
    updatedAt: int = Field(ge=0)
    purchaseDate: str = Field(default="", max_length=12)
    dueDate: str = Field(default="", max_length=12)
    lastReminderDue: str = Field(default="", max_length=12)
    closed: bool = False
    deleted: bool = False


class Payment(BaseModel):
    id: str = Field(min_length=8, max_length=64)
    debtorId: str = Field(default="", max_length=64)
    saleId: str = Field(default="", max_length=64)
    amount: int = Field(default=0, ge=0, le=MONEY)
    date: str = Field(default="", max_length=12)
    method: str = Field(default="", max_length=60)
    note: str = Field(default="", max_length=1000)
    createdAt: int = Field(ge=0)
    updatedAt: int = Field(ge=0)
    deleted: bool = False


class Sale(BaseModel):
    id: str = Field(min_length=8, max_length=64)
    productId: str = Field(default="", max_length=64)
    productName: str = Field(default="", max_length=300)
    quantity: int = Field(default=0, ge=0, le=10**7)
    unitPrice: int = Field(default=0, ge=0, le=MONEY)
    unitCost: int = Field(default=0, ge=0, le=MONEY)
    brand: str = Field(default="", max_length=120)
    buyerName: str = Field(default="", max_length=120)
    buyerPhone: str = Field(default="", max_length=40)
    date: str = Field(default="", max_length=12)
    credit: bool = False
    debtorId: str = Field(default="", max_length=64)
    creditAmount: int = Field(default=0, ge=0, le=MONEY)
    note: str = Field(default="", max_length=1000)
    createdAt: int = Field(ge=0)
    updatedAt: int = Field(ge=0)
    deleted: bool = False


class HistoryEntry(BaseModel):
    id: str = Field(min_length=8, max_length=64)
    at: int = Field(ge=0)
    who: str = Field(default="", max_length=60)
    kind: str = Field(default="", max_length=20)
    title: str = Field(default="", max_length=300)
    detail: str = Field(default="", max_length=1000)

    @property
    def updatedAt(self) -> int:  # entries never change; the time doubles as the version
        return self.at


class SyncRequest(BaseModel):
    cursor: int = Field(default=0, ge=0)
    changes: List[Product] = Field(default_factory=list, max_length=1000)
    debtors: List[Debtor] = Field(default_factory=list, max_length=1000)
    payments: List[Payment] = Field(default_factory=list, max_length=1000)
    sales: List[Sale] = Field(default_factory=list, max_length=1000)
    history: List[HistoryEntry] = Field(default_factory=list, max_length=1000)


class SyncResponse(BaseModel):
    cursor: int
    products: List[Product]
    debtors: List[Debtor]
    payments: List[Payment]
    sales: List[Sale]
    history: List[HistoryEntry]


def require_token(authorization: str = Header(default="")):
    prefix = "Bearer "
    supplied = authorization[len(prefix):] if authorization.startswith(prefix) else ""
    if not secrets.compare_digest(supplied.encode(), API_TOKEN.encode()):
        raise HTTPException(status_code=401, detail="invalid token")


app = FastAPI(title="PhoneShop inventory sync", docs_url=None, redoc_url=None)


@app.get("/health")
def health():
    return {"ok": True}


# Per table: (key column, [(db column, model attribute), ...]). Table and column names come from
# this fixed list only, never from user input.
SPECS = {
    "products": ("uuid", [
        ("category", "category"), ("brand", "brand"), ("model", "model"), ("color", "color"),
        ("storage", "storage"), ("imei", "imei"), ("buy_price", "buyPrice"),
        ("sell_price", "sellPrice"), ("quantity", "quantity"), ("note", "note"),
        ("created_at", "createdAt"), ("updated_at", "updatedAt"), ("deleted", "deleted"),
        ("photo_at", "photoAt"),
    ]),
    "debtors": ("id", [
        ("name", "name"), ("phone", "phone"), ("amount", "amount"), ("note", "note"),
        ("created_at", "createdAt"), ("updated_at", "updatedAt"),
        ("purchase_date", "purchaseDate"), ("due_date", "dueDate"),
        ("last_reminder_due", "lastReminderDue"), ("closed", "closed"), ("deleted", "deleted"),
    ]),
    "payments": ("id", [
        ("debtor_id", "debtorId"), ("sale_id", "saleId"), ("amount", "amount"), ("date", "date"),
        ("method", "method"), ("note", "note"), ("created_at", "createdAt"),
        ("updated_at", "updatedAt"), ("deleted", "deleted"),
    ]),
    "sales": ("id", [
        ("product_id", "productId"), ("product_name", "productName"), ("quantity", "quantity"),
        ("unit_price", "unitPrice"), ("unit_cost", "unitCost"), ("brand", "brand"),
        ("buyer_name", "buyerName"),
        ("buyer_phone", "buyerPhone"), ("date", "date"), ("credit", "credit"),
        ("debtor_id", "debtorId"), ("credit_amount", "creditAmount"), ("note", "note"),
        ("created_at", "createdAt"), ("updated_at", "updatedAt"), ("deleted", "deleted"),
    ]),
    "history": ("id", [
        ("at_ms", "at"), ("who", "who"), ("kind", "kind"), ("title", "title"),
        ("detail", "detail"), ("updated_at", "updatedAt"),
    ]),
}
BOOL_ATTRS = {"deleted", "closed", "credit"}


def _value(obj: BaseModel, attr: str):
    v = getattr(obj, attr)
    return int(v) if attr in BOOL_ATTRS else v


def upsert_all(conn: sqlite3.Connection, table: str, items: list, seq: int) -> int:
    """Stores the items that are newer than what the server has; returns the last used seq."""
    key, cols = SPECS[table]
    names = [key] + [c for c, _ in cols] + ["seq"]
    placeholders = ",".join("?" for _ in names)
    updates = ",".join(
        f"{c}=MAX({c}, excluded.{c})" if c == "photo_at" else f"{c}=excluded.{c}" for c, _ in cols
    ) + ",seq=excluded.seq"
    sql = (f"INSERT INTO {table} ({','.join(names)}) VALUES ({placeholders}) "
           f"ON CONFLICT({key}) DO UPDATE SET {updates}")
    for item in items:
        key_value = getattr(item, "uuid" if table == "products" else "id")
        row = conn.execute(
            f"SELECT updated_at FROM {table} WHERE {key} = ?", (key_value,)
        ).fetchone()
        if row is not None and item.updatedAt <= row["updated_at"]:
            continue  # server already has an equal or newer version
        seq += 1
        conn.execute(sql, [key_value] + [_value(item, a) for _, a in cols] + [seq])
    return seq


def read_since(conn: sqlite3.Connection, table: str, cursor: int):
    return conn.execute(f"SELECT * FROM {table} WHERE seq > ? ORDER BY seq", (cursor,)).fetchall()


def row_to_history(r: sqlite3.Row) -> HistoryEntry:
    return HistoryEntry(
        id=r["id"], at=r["at_ms"], who=r["who"], kind=r["kind"], title=r["title"], detail=r["detail"],
    )


def row_to_product(r: sqlite3.Row) -> Product:
    return Product(
        uuid=r["uuid"], category=r["category"], brand=r["brand"], model=r["model"],
        color=r["color"], storage=r["storage"], imei=r["imei"],
        buyPrice=r["buy_price"], sellPrice=r["sell_price"], quantity=r["quantity"],
        note=r["note"], createdAt=r["created_at"], updatedAt=r["updated_at"],
        deleted=bool(r["deleted"]), photoAt=r["photo_at"],
    )


def row_to_debtor(r: sqlite3.Row) -> Debtor:
    return Debtor(
        id=r["id"], name=r["name"], phone=r["phone"], amount=r["amount"], note=r["note"],
        createdAt=r["created_at"], updatedAt=r["updated_at"],
        purchaseDate=r["purchase_date"], dueDate=r["due_date"],
        lastReminderDue=r["last_reminder_due"],
        closed=bool(r["closed"]), deleted=bool(r["deleted"]),
    )


def row_to_payment(r: sqlite3.Row) -> Payment:
    return Payment(
        id=r["id"], debtorId=r["debtor_id"], saleId=r["sale_id"], amount=r["amount"],
        date=r["date"], method=r["method"], note=r["note"],
        createdAt=r["created_at"], updatedAt=r["updated_at"], deleted=bool(r["deleted"]),
    )


def row_to_sale(r: sqlite3.Row) -> Sale:
    return Sale(
        id=r["id"], productId=r["product_id"], productName=r["product_name"],
        quantity=r["quantity"], unitPrice=r["unit_price"], unitCost=r["unit_cost"],
        brand=r["brand"], buyerName=r["buyer_name"], buyerPhone=r["buyer_phone"], date=r["date"],
        credit=bool(r["credit"]), debtorId=r["debtor_id"], creditAmount=r["credit_amount"],
        note=r["note"], createdAt=r["created_at"], updatedAt=r["updated_at"],
        deleted=bool(r["deleted"]),
    )


@app.post("/sync", response_model=SyncResponse, dependencies=[Depends(require_token)])
def sync(req: SyncRequest):
    with _lock, closing(connect()) as conn:
        conn.execute("BEGIN IMMEDIATE")
        # One counter for all tables, so a single cursor on the phone covers everything.
        seq = max(
            conn.execute(f"SELECT COALESCE(MAX(seq), 0) FROM {t}").fetchone()[0] for t in TABLES
        )
        seq = upsert_all(conn, "products", req.changes, seq)
        seq = upsert_all(conn, "debtors", req.debtors, seq)
        seq = upsert_all(conn, "payments", req.payments, seq)
        seq = upsert_all(conn, "sales", req.sales, seq)
        seq = upsert_all(conn, "history", req.history, seq)

        product_rows = read_since(conn, "products", req.cursor)
        debtor_rows = read_since(conn, "debtors", req.cursor)
        payment_rows = read_since(conn, "payments", req.cursor)
        sale_rows = read_since(conn, "sales", req.cursor)
        history_rows = read_since(conn, "history", req.cursor)
        conn.commit()

    all_seqs = [
        r["seq"] for rows in (product_rows, debtor_rows, payment_rows, sale_rows, history_rows) for r in rows
    ]
    return SyncResponse(
        cursor=max([req.cursor] + all_seqs),
        products=[row_to_product(r) for r in product_rows],
        debtors=[row_to_debtor(r) for r in debtor_rows],
        payments=[row_to_payment(r) for r in payment_rows],
        sales=[row_to_sale(r) for r in sale_rows],
        history=[row_to_history(r) for r in history_rows],
    )


# ───────────── product photos ─────────────
PHOTO_DIR = os.environ.get("PHOTO_DIR", os.path.join(os.path.dirname(os.path.abspath(DB_PATH)), "photos"))
os.makedirs(PHOTO_DIR, exist_ok=True)
MAX_PHOTO_BYTES = 2 * 1024 * 1024
_UUID_RE = re.compile(r"^[A-Za-z0-9-]{8,64}$")
_AT_RE = re.compile(r"^[0-9]{1,16}$")


def _photo_path(uuid: str, at: str) -> str:
    if not _UUID_RE.match(uuid) or not _AT_RE.match(at):
        raise HTTPException(status_code=400, detail="bad photo id")
    return os.path.join(PHOTO_DIR, f"{uuid}_{at}.jpg")


@app.put("/photo/{uuid}/{at}", dependencies=[Depends(require_token)])
async def put_photo(uuid: str, at: str, request: Request):
    path = _photo_path(uuid, at)
    data = await request.body()
    if len(data) > MAX_PHOTO_BYTES:
        raise HTTPException(status_code=413, detail="photo too large")
    if not data.startswith(b"\xff\xd8"):
        raise HTTPException(status_code=415, detail="only JPEG photos are accepted")
    tmp = path + ".part"
    with open(tmp, "wb") as f:
        f.write(data)
    os.replace(tmp, path)
    # drop older versions of the same product's photo
    for name in os.listdir(PHOTO_DIR):
        if name.startswith(uuid + "_") and name.endswith(".jpg") and name != os.path.basename(path):
            try:
                if int(name[len(uuid) + 1:-4]) < int(at):
                    os.remove(os.path.join(PHOTO_DIR, name))
            except (ValueError, OSError):
                pass
    return {"ok": True}


@app.get("/photo/{uuid}/{at}", dependencies=[Depends(require_token)])
def get_photo(uuid: str, at: str):
    path = _photo_path(uuid, at)
    if not os.path.isfile(path):
        raise HTTPException(status_code=404, detail="no such photo")
    return FileResponse(path, media_type="image/jpeg")
