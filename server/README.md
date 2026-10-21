# سرور همگام‌سازی موجودی

سرور کوچک (FastAPI + SQLite) که موجودی را بین چند گوشی یکی می‌کند.

## راه‌اندازی روی VPS (اوبونتو)

```bash
sudo apt install -y python3-venv caddy
sudo useradd -r -m shopsync
sudo -u shopsync bash -c '
  cd ~ && python3 -m venv venv
  mkdir -p app && cd app
'
# main.py و requirements.txt را داخل /home/shopsync/app کپی کنید، سپس:
sudo -u shopsync /home/shopsync/venv/bin/pip install -r /home/shopsync/app/requirements.txt
```

توکن تصادفی بسازید و نگه دارید (همین را در اپ وارد می‌کنید):

```bash
openssl rand -hex 24
```

سرویس systemd: فایل `/etc/systemd/system/shopsync.service`

```ini
[Unit]
Description=Shop inventory sync
After=network.target

[Service]
User=shopsync
WorkingDirectory=/home/shopsync/app
Environment=API_TOKEN=توکن_شما
Environment=DB_PATH=/home/shopsync/inventory.db
ExecStart=/home/shopsync/venv/bin/uvicorn main:app --host 127.0.0.1 --port 8000
Restart=always

[Install]
WantedBy=multi-user.target
```

```bash
sudo systemctl enable --now shopsync
```

## HTTPS (لازم است)

اپ فقط به آدرس `https://` وصل می‌شود. با دامنه‌ای که به IP سرور اشاره می‌کند، فایل `/etc/caddy/Caddyfile`:

```
shop.example.com {
    reverse_proxy 127.0.0.1:8000
}
```

```bash
sudo systemctl reload caddy
```

Caddy گواهی را خودش می‌گیرد و تمدید می‌کند. بعد در اپ: ⁠آیکن ابر ← آدرس `https://shop.example.com` و توکن.

## بکاپ

فایل `inventory.db` همه‌ی داده است. روزانه یک کپی بگیرید:

```bash
sqlite3 /home/shopsync/inventory.db ".backup '/home/shopsync/backup-$(date +%F).db'"
```

## رفتار همگام‌سازی

- هر گوشی تغییرات خودش را می‌فرستد و تغییرات بقیه را می‌گیرد (هر ۶۰ ثانیه، و بعد از هر ثبت).
- اگر دو گوشی یک کالا را ویرایش کنند، **آخرین ویرایش** می‌ماند.
- حذف کالا هم همگام می‌شود.

## به‌روزرسانی به نسخه‌ی ۲٫۰ (بدهکاران، پرداخت‌ها، فروش)

1. فایل جدید `main.py` را جای قبلی در `/home/shopsync/app` کپی کنید.
2. `sudo systemctl restart shopsync`

نیازی به پاک‌کردن دیتابیس نیست؛ جدول‌های جدید خودکار ساخته می‌شوند و کالاها می‌مانند. اپ‌های قدیمی هم کار می‌کنند (فقط کالاها را همگام می‌کنند). حتماً همه‌ی گوشی‌ها را به اپ جدید ببرید تا بدهکاران و فروش‌ها بین‌شان یکی شود.
