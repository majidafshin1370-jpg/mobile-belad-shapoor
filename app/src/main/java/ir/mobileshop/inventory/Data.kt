package ir.mobileshop.inventory

import android.content.Context
import androidx.room.ColumnInfo
import androidx.room.Dao
import androidx.room.Database
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.Transaction
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import kotlinx.coroutines.flow.Flow
import java.util.UUID

/**
 * [uuid] is created on the phone, so several phones can add products offline without clashing.
 * [updatedAt] decides the winner when two phones edit the same product (newest wins).
 * Deleting only sets [deleted] so the deletion can be synced to the other phones.
 * [dirty] means "changed here, not yet uploaded".
 */
@Entity(tableName = "products")
data class Product(
    @PrimaryKey val uuid: String = UUID.randomUUID().toString(),
    val category: String = "گوشی",
    val brand: String = "",
    val model: String = "",
    val color: String = "",
    val storage: String = "",
    val imei: String = "",
    val buyPrice: Long = 0,
    val sellPrice: Long = 0,
    val quantity: Int = 0,
    val note: String = "",
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis(),
    val deleted: Boolean = false,
    val dirty: Boolean = true,
    /** 0 = no photo; otherwise the time the current photo was taken (part of its file name). */
    @ColumnInfo(defaultValue = "0") val photoAt: Long = 0
)

/**
 * A customer who owes money. Synced like products ([deleted] = tombstone, [dirty] = not uploaded yet).
 * [amount] is the debt entered by hand; money added by credit sales lives in [Sale.creditAmount]
 * and money received lives in [Payment], so two phones never edit the same number.
 * [closed] = the debtor was marked «تسویه شد».
 * [purchaseDate] and [dueDate] are Shamsi dates written as yyyy/MM/dd.
 * [lastReminderDue] remembers which due date an SMS reminder was already sent for.
 */
@Entity(tableName = "debtors")
data class Debtor(
    @PrimaryKey val id: String = UUID.randomUUID().toString(),
    val name: String,
    val phone: String = "",
    val amount: Long = 0,
    val note: String = "",
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis(),
    @ColumnInfo(defaultValue = "''") val purchaseDate: String = "",
    @ColumnInfo(defaultValue = "''") val dueDate: String = "",
    @ColumnInfo(defaultValue = "''") val lastReminderDue: String = "",
    @ColumnInfo(defaultValue = "0") val closed: Boolean = false,
    @ColumnInfo(defaultValue = "0") val deleted: Boolean = false,
    @ColumnInfo(defaultValue = "1") val dirty: Boolean = true
)

/** One partial payment from a debtor (cash, cheque, instalment...). [date] is Shamsi yyyy/MM/dd. */
@Entity(tableName = "payments")
data class Payment(
    @PrimaryKey val id: String = UUID.randomUUID().toString(),
    val debtorId: String,
    /** Set when this is the down payment of a credit sale, so cancelling the sale removes it too. */
    val saleId: String = "",
    val amount: Long,
    val date: String,
    val method: String = "",
    val note: String = "",
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis(),
    val deleted: Boolean = false,
    val dirty: Boolean = true
)

/**
 * One sale. Product name and prices are copied here so reports stay correct even if the product
 * is edited or deleted later. [creditAmount] is the part not paid at sale time (goes to [debtorId]).
 */
@Entity(tableName = "sales")
data class Sale(
    @PrimaryKey val id: String = UUID.randomUUID().toString(),
    val productId: String,
    val productName: String,
    val quantity: Int,
    val unitPrice: Long,
    val unitCost: Long,
    @ColumnInfo(defaultValue = "''") val brand: String = "",
    val buyerName: String = "",
    val buyerPhone: String = "",
    val date: String,
    val credit: Boolean = false,
    val debtorId: String = "",
    val creditAmount: Long = 0,
    val note: String = "",
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis(),
    val deleted: Boolean = false,
    val dirty: Boolean = true
)

val Sale.total: Long get() = unitPrice * quantity
val Sale.profit: Long get() = (unitPrice - unitCost) * quantity

/**
 * One line of the change log: who did what and when. Entries are never edited, only added, and they are
 * synced to every phone. [kind] is product / stock / sale / debtor.
 */
@Entity(tableName = "history")
data class HistoryEntry(
    @PrimaryKey val id: String = UUID.randomUUID().toString(),
    val happenedAt: Long = System.currentTimeMillis(),
    val who: String,
    val kind: String,
    val title: String,
    val detail: String,
    val dirty: Boolean = true
)

/** Debt = hand-entered amount + credit sales − payments (never below zero). */
fun debtorRemaining(d: Debtor, payments: List<Payment>, sales: List<Sale>): Long {
    val charged = d.amount + sales.filter { it.debtorId == d.id }.sumOf { it.creditAmount }
    val paid = payments.filter { it.debtorId == d.id }.sumOf { it.amount }
    return (charged - paid).coerceAtLeast(0L)
}

@Dao
abstract class ProductDao {
    @Query("SELECT * FROM products WHERE deleted = 0 ORDER BY createdAt DESC")
    abstract fun observeAll(): Flow<List<Product>>

    @Query("SELECT * FROM products WHERE dirty = 1")
    abstract suspend fun getDirty(): List<Product>

    @Query("SELECT * FROM products WHERE uuid = :uuid")
    abstract suspend fun get(uuid: String): Product?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    abstract suspend fun upsert(product: Product)

    @Query("SELECT * FROM products WHERE deleted = 0 AND photoAt > 0")
    abstract suspend fun getProductsWithPhoto(): List<Product>

    // ── debtors ──
    @Query("SELECT * FROM debtors WHERE deleted = 0 ORDER BY createdAt DESC")
    abstract fun observeDebtors(): Flow<List<Debtor>>

    /** Debtors that can still receive SMS reminders. */
    @Query("SELECT * FROM debtors WHERE deleted = 0 AND closed = 0")
    abstract suspend fun getOpenDebtors(): List<Debtor>

    @Query("SELECT * FROM debtors WHERE dirty = 1")
    abstract suspend fun getDirtyDebtors(): List<Debtor>

    @Query("SELECT * FROM debtors WHERE id = :id")
    abstract suspend fun getDebtor(id: String): Debtor?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    abstract suspend fun upsertDebtor(debtor: Debtor)

    // ── payments ──
    @Query("SELECT * FROM payments WHERE deleted = 0 ORDER BY date DESC, createdAt DESC")
    abstract fun observePayments(): Flow<List<Payment>>

    @Query("SELECT * FROM payments WHERE deleted = 0")
    abstract suspend fun getPayments(): List<Payment>

    @Query("SELECT * FROM payments WHERE deleted = 0 AND debtorId = :debtorId")
    abstract suspend fun getPaymentsOfDebtor(debtorId: String): List<Payment>

    @Query("SELECT * FROM payments WHERE deleted = 0 AND saleId = :saleId")
    abstract suspend fun getPaymentsOfSale(saleId: String): List<Payment>

    @Query("SELECT * FROM payments WHERE dirty = 1")
    abstract suspend fun getDirtyPayments(): List<Payment>

    @Query("SELECT * FROM payments WHERE id = :id")
    abstract suspend fun getPayment(id: String): Payment?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    abstract suspend fun upsertPayment(payment: Payment)

    // ── sales ──
    @Query("SELECT * FROM sales WHERE deleted = 0 ORDER BY date DESC, createdAt DESC")
    abstract fun observeSales(): Flow<List<Sale>>

    @Query("SELECT * FROM sales WHERE deleted = 0 AND debtorId != ''")
    abstract suspend fun getCreditSales(): List<Sale>

    @Query("SELECT * FROM sales WHERE dirty = 1")
    abstract suspend fun getDirtySales(): List<Sale>

    @Query("SELECT * FROM sales WHERE id = :id")
    abstract suspend fun getSale(id: String): Sale?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    abstract suspend fun upsertSale(sale: Sale)

    // ── history ──
    @Query("SELECT * FROM history ORDER BY happenedAt DESC LIMIT 1000")
    abstract fun observeHistory(): Flow<List<HistoryEntry>>

    @Query("SELECT * FROM history WHERE dirty = 1")
    abstract suspend fun getDirtyHistory(): List<HistoryEntry>

    @Query("SELECT * FROM history WHERE id = :id")
    abstract suspend fun getHistoryEntry(id: String): HistoryEntry?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    abstract suspend fun upsertHistory(entry: HistoryEntry)

    @Query("UPDATE history SET dirty = 1")
    abstract suspend fun markAllHistoryDirty()

    @Query("UPDATE products SET dirty = 1")
    abstract suspend fun markAllProductsDirty()

    @Query("UPDATE debtors SET dirty = 1")
    abstract suspend fun markAllDebtorsDirty()

    @Query("UPDATE payments SET dirty = 1")
    abstract suspend fun markAllPaymentsDirty()

    @Query("UPDATE sales SET dirty = 1")
    abstract suspend fun markAllSalesDirty()

    @Transaction
    open suspend fun markAllDirty() {
        markAllProductsDirty()
        markAllDebtorsDirty()
        markAllPaymentsDirty()
        markAllSalesDirty()
        markAllHistoryDirty()
    }

    /** Stores a sale together with its stock change, debtor and down payment, all or nothing. */
    @Transaction
    open suspend fun recordSale(product: Product, sale: Sale, debtor: Debtor?, downPayment: Payment?) {
        upsert(product)
        debtor?.let { upsertDebtor(it) }
        upsertSale(sale)
        downPayment?.let { upsertPayment(it) }
    }

    /** Cancels a sale: stock goes back, its credit and down payment disappear from the debtor. */
    @Transaction
    open suspend fun cancelSale(saleId: String, now: Long) {
        val sale = getSale(saleId) ?: return
        if (sale.deleted) return
        upsertSale(sale.copy(deleted = true, updatedAt = now, dirty = true))
        val product = get(sale.productId)
        if (product != null && !product.deleted) {
            upsert(product.copy(quantity = product.quantity + sale.quantity, updatedAt = now, dirty = true))
        }
        for (p in getPaymentsOfSale(sale.id)) {
            upsertPayment(p.copy(deleted = true, updatedAt = now, dirty = true))
        }
    }

    /** Removes a debtor and all of its payments (as tombstones, so other phones delete them too). */
    @Transaction
    open suspend fun deleteDebtorCascade(id: String, now: Long) {
        val d = getDebtor(id) ?: return
        upsertDebtor(d.copy(deleted = true, updatedAt = now, dirty = true))
        for (p in getPaymentsOfDebtor(id)) {
            upsertPayment(p.copy(deleted = true, updatedAt = now, dirty = true))
        }
    }

    /** Records the final payment (if any money is left) and closes the debtor. */
    @Transaction
    open suspend fun settleDebtor(debtor: Debtor, finalPayment: Payment?, now: Long) {
        finalPayment?.let { upsertPayment(it) }
        upsertDebtor(debtor.copy(closed = true, updatedAt = now, dirty = true))
    }

    /**
     * Applies one sync round in a single transaction:
     * 1. records we just uploaded become clean (unless edited again meanwhile),
     * 2. records from the server overwrite local ones, except local edits that are newer.
     */
    @Transaction
    open suspend fun applySync(
        pushedProducts: List<Product>, pulledProducts: List<Product>,
        pushedDebtors: List<Debtor>, pulledDebtors: List<Debtor>,
        pushedPayments: List<Payment>, pulledPayments: List<Payment>,
        pushedSales: List<Sale>, pulledSales: List<Sale>,
        pushedHistory: List<HistoryEntry>, pulledHistory: List<HistoryEntry>
    ) {
        for (p in pushedProducts) {
            val local = get(p.uuid) ?: continue
            if (local.updatedAt == p.updatedAt) upsert(local.copy(dirty = false))
        }
        for (remote in pulledProducts) {
            val local = get(remote.uuid)
            if (local != null && local.dirty && local.updatedAt > remote.updatedAt) continue
            upsert(remote.copy(dirty = false))
        }

        for (p in pushedDebtors) {
            val local = getDebtor(p.id) ?: continue
            if (local.updatedAt == p.updatedAt) upsertDebtor(local.copy(dirty = false))
        }
        for (remote in pulledDebtors) {
            val local = getDebtor(remote.id)
            if (local != null && local.dirty && local.updatedAt > remote.updatedAt) continue
            upsertDebtor(remote.copy(dirty = false))
        }

        for (p in pushedPayments) {
            val local = getPayment(p.id) ?: continue
            if (local.updatedAt == p.updatedAt) upsertPayment(local.copy(dirty = false))
        }
        for (remote in pulledPayments) {
            val local = getPayment(remote.id)
            if (local != null && local.dirty && local.updatedAt > remote.updatedAt) continue
            upsertPayment(remote.copy(dirty = false))
        }

        for (p in pushedSales) {
            val local = getSale(p.id) ?: continue
            if (local.updatedAt == p.updatedAt) upsertSale(local.copy(dirty = false))
        }
        for (remote in pulledSales) {
            val local = getSale(remote.id)
            if (local != null && local.dirty && local.updatedAt > remote.updatedAt) continue
            upsertSale(remote.copy(dirty = false))
        }

        // History lines never change, so there is nothing to compare.
        for (p in pushedHistory) {
            val local = getHistoryEntry(p.id) ?: continue
            upsertHistory(local.copy(dirty = false))
        }
        for (remote in pulledHistory) upsertHistory(remote.copy(dirty = false))
    }
}

/** Adds the two dates and the "reminder sent" marker without losing existing debtors. */
val MIGRATION_3_4 = object : Migration(3, 4) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE debtors ADD COLUMN purchaseDate TEXT NOT NULL DEFAULT ''")
        db.execSQL("ALTER TABLE debtors ADD COLUMN dueDate TEXT NOT NULL DEFAULT ''")
        db.execSQL("ALTER TABLE debtors ADD COLUMN lastReminderDue TEXT NOT NULL DEFAULT ''")
    }
}

/**
 * Debtors become syncable (existing ones are marked "not uploaded yet" so they go to the server
 * on the next sync) and the payments and sales tables are created. No data is lost.
 */
val MIGRATION_4_5 = object : Migration(4, 5) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE debtors ADD COLUMN closed INTEGER NOT NULL DEFAULT 0")
        db.execSQL("ALTER TABLE debtors ADD COLUMN deleted INTEGER NOT NULL DEFAULT 0")
        db.execSQL("ALTER TABLE debtors ADD COLUMN dirty INTEGER NOT NULL DEFAULT 1")
        db.execSQL(
            "CREATE TABLE IF NOT EXISTS payments (" +
                "id TEXT NOT NULL, debtorId TEXT NOT NULL, saleId TEXT NOT NULL, " +
                "amount INTEGER NOT NULL, date TEXT NOT NULL, method TEXT NOT NULL, note TEXT NOT NULL, " +
                "createdAt INTEGER NOT NULL, updatedAt INTEGER NOT NULL, " +
                "deleted INTEGER NOT NULL, dirty INTEGER NOT NULL, PRIMARY KEY(id))"
        )
        db.execSQL(
            "CREATE TABLE IF NOT EXISTS sales (" +
                "id TEXT NOT NULL, productId TEXT NOT NULL, productName TEXT NOT NULL, " +
                "quantity INTEGER NOT NULL, unitPrice INTEGER NOT NULL, unitCost INTEGER NOT NULL, " +
                "buyerName TEXT NOT NULL, buyerPhone TEXT NOT NULL, date TEXT NOT NULL, " +
                "credit INTEGER NOT NULL, debtorId TEXT NOT NULL, creditAmount INTEGER NOT NULL, " +
                "note TEXT NOT NULL, createdAt INTEGER NOT NULL, updatedAt INTEGER NOT NULL, " +
                "deleted INTEGER NOT NULL, dirty INTEGER NOT NULL, PRIMARY KEY(id))"
        )
    }
}

/**
 * Products get an optional photo (the picture itself is a file, only its version number is stored),
 * sales remember the brand, and the change log table is created.
 */
val MIGRATION_5_6 = object : Migration(5, 6) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE products ADD COLUMN photoAt INTEGER NOT NULL DEFAULT 0")
        db.execSQL("ALTER TABLE sales ADD COLUMN brand TEXT NOT NULL DEFAULT ''")
        db.execSQL(
            "CREATE TABLE IF NOT EXISTS history (" +
                "id TEXT NOT NULL, happenedAt INTEGER NOT NULL, who TEXT NOT NULL, kind TEXT NOT NULL, " +
                "title TEXT NOT NULL, detail TEXT NOT NULL, dirty INTEGER NOT NULL, PRIMARY KEY(id))"
        )
    }
}

@Database(
    entities = [Product::class, Debtor::class, Payment::class, Sale::class, HistoryEntry::class],
    version = 6,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun productDao(): ProductDao

    companion object {
        @Volatile private var instance: AppDatabase? = null

        fun get(context: Context): AppDatabase =
            instance ?: synchronized(this) {
                instance ?: Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "phone_shop.db"
                )
                    .addMigrations(MIGRATION_3_4, MIGRATION_4_5, MIGRATION_5_6)
                    // v1 (local-only test build) had a different key; its data is not carried over.
                    .fallbackToDestructiveMigration()
                    .build().also { instance = it }
            }
    }
}
