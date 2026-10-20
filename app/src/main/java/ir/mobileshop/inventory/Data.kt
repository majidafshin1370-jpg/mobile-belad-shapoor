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
    val dirty: Boolean = true
)

/**
 * A customer who owes money. Stored only on this phone (not synced).
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
    @ColumnInfo(defaultValue = "''") val lastReminderDue: String = ""
)

@Dao
abstract class ProductDao {
    @Query("SELECT * FROM products WHERE deleted = 0 ORDER BY createdAt DESC")
    abstract fun observeAll(): Flow<List<Product>>

    @Query("SELECT * FROM products WHERE dirty = 1")
    abstract suspend fun getDirty(): List<Product>

    @Query("SELECT * FROM products WHERE uuid = :uuid")
    abstract suspend fun get(uuid: String): Product?

    @Query("SELECT * FROM debtors ORDER BY createdAt DESC")
    abstract fun observeDebtors(): Flow<List<Debtor>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    abstract suspend fun upsertDebtor(debtor: Debtor)

    @Query("SELECT * FROM debtors")
    abstract suspend fun getDebtors(): List<Debtor>

    @Query("DELETE FROM debtors WHERE id = :id")
    abstract suspend fun deleteDebtor(id: String)

    @Query("UPDATE products SET dirty = 1")
    abstract suspend fun markAllDirty()

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    abstract suspend fun upsert(product: Product)

    /**
     * Applies one sync round in a single transaction:
     * 1. products we just uploaded become clean (unless edited again meanwhile),
     * 2. products from the server overwrite local ones, except local edits that are newer.
     */
    @Transaction
    open suspend fun applySync(pushed: List<Product>, pulled: List<Product>) {
        for (p in pushed) {
            val local = get(p.uuid) ?: continue
            if (local.updatedAt == p.updatedAt) upsert(local.copy(dirty = false))
        }
        for (remote in pulled) {
            val local = get(remote.uuid)
            if (local != null && local.dirty && local.updatedAt > remote.updatedAt) continue
            upsert(remote.copy(dirty = false))
        }
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

@Database(entities = [Product::class, Debtor::class], version = 4, exportSchema = false)
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
                    .addMigrations(MIGRATION_3_4)
                    // v1 (local-only test build) had a different key; its data is not carried over.
                    .fallbackToDestructiveMigration()
                    .build().also { instance = it }
            }
    }
}
