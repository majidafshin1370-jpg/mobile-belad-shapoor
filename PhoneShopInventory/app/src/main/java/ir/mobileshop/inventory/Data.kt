package ir.mobileshop.inventory

import android.content.Context
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

@Dao
abstract class ProductDao {
    @Query("SELECT * FROM products WHERE deleted = 0 ORDER BY createdAt DESC")
    abstract fun observeAll(): Flow<List<Product>>

    @Query("SELECT * FROM products WHERE dirty = 1")
    abstract suspend fun getDirty(): List<Product>

    @Query("SELECT * FROM products WHERE uuid = :uuid")
    abstract suspend fun get(uuid: String): Product?

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


@Entity(tableName = "invoices")
data class Invoice(@PrimaryKey val id: String = UUID.randomUUID().toString(), val productId: String, val productName: String, val quantity: Int, val unitPrice: Long, val unitCost: Long, val createdAt: Long = System.currentTimeMillis(), val customer: String = "", val paid: Long = 0) 
@Entity(tableName = "debtors")
data class Debtor(@PrimaryKey val id: String = UUID.randomUUID().toString(), val name: String, val phone: String = "", val amount: Long, val settled: Boolean = false, val createdAt: Long = System.currentTimeMillis())
@Entity(tableName = "reminders")
data class Reminder(@PrimaryKey val id: String = UUID.randomUUID().toString(), val title: String, val dueAt: Long, val type: String = "گارانتی", val note: String = "", val done: Boolean = false)
@Entity(tableName = "repairs")
data class Repair(@PrimaryKey val id: String = UUID.randomUUID().toString(), val device: String, val issue: String, val status: String = "در انتظار", val cost: Long = 0, val createdAt: Long = System.currentTimeMillis())

@Dao
abstract class BusinessDao {
 @Query("SELECT * FROM invoices ORDER BY createdAt DESC") abstract fun invoices(): Flow<List<Invoice>>
 @Query("SELECT * FROM debtors ORDER BY createdAt DESC") abstract fun debtors(): Flow<List<Debtor>>
 @Query("SELECT * FROM reminders ORDER BY dueAt") abstract fun reminders(): Flow<List<Reminder>>
 @Query("SELECT * FROM repairs ORDER BY createdAt DESC") abstract fun repairs(): Flow<List<Repair>>
 @Insert abstract suspend fun invoice(i: Invoice)
 @Insert(onConflict = OnConflictStrategy.REPLACE) abstract suspend fun saveDebtor(d: Debtor)
 @Insert(onConflict = OnConflictStrategy.REPLACE) abstract suspend fun saveReminder(r: Reminder)
 @Insert(onConflict = OnConflictStrategy.REPLACE) abstract suspend fun saveRepair(r: Repair)
 @Query("DELETE FROM debtors WHERE id=:id") abstract suspend fun deleteDebtor(id: String)
 @Query("UPDATE products SET quantity=quantity-:qty, updatedAt=:now, dirty=1 WHERE uuid=:pid AND quantity>=:qty") abstract suspend fun decrement(pid:String, qty:Int, now:Long):Int
 @Transaction open suspend fun checkout(p: Product, qty:Int, customer:String, paid:Long) {
   require(qty>0); check(decrement(p.uuid,qty,System.currentTimeMillis())==1) { "موجودی کافی نیست" }
   invoice(Invoice(productId=p.uuid, productName="${p.brand} ${p.model}", quantity=qty, unitPrice=p.sellPrice, unitCost=p.buyPrice, customer=customer, paid=paid))
   if (paid < p.sellPrice*qty) saveDebtor(Debtor(name=customer.ifBlank { "مشتری فروش" }, amount=p.sellPrice*qty-paid))
 }
}

@Database(entities = [Product::class, Invoice::class, Debtor::class, Reminder::class, Repair::class], version = 3, exportSchema = false)
abstract class AppDatabase : RoomDatabase() {
    abstract fun productDao(): ProductDao
    abstract fun businessDao(): BusinessDao

    companion object {
        @Volatile private var instance: AppDatabase? = null

        fun get(context: Context): AppDatabase =
            instance ?: synchronized(this) {
                instance ?: Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "phone_shop.db"
                )
                    // v1 (local-only test build) had a different key; its data is not carried over.
                    .fallbackToDestructiveMigration()
                    .build().also { instance = it }
            }
    }
}
