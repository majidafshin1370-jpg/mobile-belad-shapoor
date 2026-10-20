# فروشگاه بلاد شاپور (PhoneShopInventory)

اپ اندروید (Kotlin + Jetpack Compose + Room) برای ثبت و گزارش موجودی، با همگام‌سازی آفلاین‌محور بین چند گوشی. رابط فارسی و راست‌به‌چپ.

## ساختار
- `app/` — اپ اندروید
- `server/` — سرور همگام‌سازی (راه‌اندازی در `server/README.md`)

## امکانات
- ثبت و ویرایش کالا: دسته، برند، مدل، رنگ، حافظه، IMEI، قیمت خرید/فروش، تعداد، توضیحات
- جستجو و فیلتر دسته، دکمه‌ی «فروش ۱ عدد»، هشدار موجودی کم
- گزارش: ارزش موجودی، سود احتمالی، موجودی هر برند، کالاهای رو به اتمام
- خروجی CSV برای اکسل
- **همگام‌سازی:** کار بدون اینترنت ممکن است؛ تغییرات در گوشی ذخیره می‌شود و با وصل شدن اینترنت به سرور می‌رود و تغییرات گوشی‌های دیگر دریافت می‌شود

## ظاهر
پوسته‌ی روشن به سبک فروشگاه‌های اینترنتی، فونت وزیرمتن (مجوز OFL در پوشه‌ی `licenses/`)، ارقام فارسی، بنر عکس‌های بلاد شاپور بالای هر صفحه و منوی پایین (موجودی، گزارش، اتصال).

## اجرا
1. سرور را طبق `server/README.md` روی VPS راه بیندازید.
2. پوشه‌ی پروژه را در Android Studio باز کنید و Run بزنید.
3. در اپ: آیکن ابر ← آدرس https و توکن را وارد کنید (روی همه‌ی گوشی‌ها یکسان).

## محدودیت‌ها
- اگر دو گوشی هم‌زمان (مثلاً هر دو آفلاین) یک کالا را ویرایش کنند، آخرین ویرایش می‌ماند؛ مثلاً دو «فروش ۱ عدد» هم‌زمان روی یک کالا ممکن است فقط یک بار شمرده شود.
- نسخه‌ی قبلی (فقط محلی) داده‌اش به این نسخه منتقل نمی‌شود.

## Update (matched to the installed APK)
Added from the newer APK: debtors tab (Debtor entity, DB v3), brand filter + sort chips, Shamsi/Gregorian date line (JalaliDates), app name «موبایل بلاد شاپور».
These parts were rewritten from decompiled code, so UI spacing may differ slightly from the original.

### Debtors: purchase date, due date, automatic SMS
- Debtor has `purchaseDate`, `dueDate` (Shamsi, yyyy/MM/dd) and `lastReminderDue`; DB v4 with a real migration (existing debtors are kept).
- `Reminder.kt`: WorkManager job (every 3h, only 09:00–21:00) sends one SMS per due date, N days before it (default 3, adjustable in the Debtors tab). Needs the SEND_SMS permission (asked when you switch it on).
- `JalaliDates`: fixed an off-by-one-day bug in the Gregorian→Shamsi conversion, added Shamsi parsing/days-until.


## تغییرات CI
- AGP به 8.6.1 ارتقا یافت (سازگار با compileSdk 35 و Gradle 8.9).
- مخازن google() و mavenCentral() در settings.gradle.kts صریح و با content-filtering تنظیم شدند.
- در کد این نسخه اسکنر CameraX/ML Kit استفاده نشده؛ بنابراین نیازهای minSdk/repository مربوط به آن‌ها علت خطای این پروژه نیست. وابستگی Compose Animation که در کد استفاده می‌شود صریحاً افزوده شد.
