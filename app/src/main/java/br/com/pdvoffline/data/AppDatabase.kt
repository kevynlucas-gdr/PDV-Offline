package br.com.pdvoffline.data

import android.content.Context
import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Entity(tableName = "products", indices = [Index(value = ["code"], unique = true)])
data class Product(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val code: String,
    val salePrice: Long,
    val costPrice: Long = 0,
    val stock: Double = 0.0,
    val isService: Boolean = false,
    val active: Boolean = true,
    val photoUri: String? = null
)

@Entity(tableName = "customers")
data class Customer(@PrimaryKey(autoGenerate = true) val id: Long = 0, val name: String, val phone: String = "")
@Entity(tableName = "sales")
data class Sale(@PrimaryKey(autoGenerate = true) val id: Long = 0, val createdAt: Long = System.currentTimeMillis(), val customerId: Long? = null, val total: Long, val discount: Long = 0, val status: String = "FINALIZADA")
@Entity(tableName = "sale_items")
data class SaleItem(@PrimaryKey(autoGenerate = true) val id: Long = 0, val saleId: Long, val productId: Long, val productName: String, val quantity: Double, val unitPrice: Long, val unitCost: Long, val lineTotal: Long)
@Entity(tableName = "payments")
data class Payment(@PrimaryKey(autoGenerate = true) val id: Long = 0, val saleId: Long, val method: String, val amount: Long, val createdAt: Long = System.currentTimeMillis())
@Entity(tableName = "cash_sessions")
data class CashSession(@PrimaryKey(autoGenerate = true) val id: Long = 0, val openedAt: Long = System.currentTimeMillis(), val closedAt: Long? = null, val openingFloat: Long, val closingCount: Long? = null, val expectedCash: Long? = null, val difference: Long? = null, val isOpen: Boolean = true)
@Entity(tableName = "cash_movements")
data class CashMovement(@PrimaryKey(autoGenerate = true) val id: Long = 0, val sessionId: Long, val type: String, val amount: Long, val description: String, val createdAt: Long = System.currentTimeMillis())
@Entity(tableName = "financial_entries")
data class FinancialEntry(@PrimaryKey(autoGenerate = true) val id: Long = 0, val kind: String, val description: String, val amount: Long, val paid: Boolean = false, val customerId: Long? = null, val createdAt: Long = System.currentTimeMillis())

@Dao
interface PdvDao {
    @Query("SELECT * FROM products WHERE active = 1 ORDER BY name") fun observeProducts(): Flow<List<Product>>
    @Query("SELECT * FROM products WHERE id = :id") suspend fun product(id: Long): Product?
    @Insert(onConflict = OnConflictStrategy.ABORT) suspend fun insertProduct(product: Product): Long
    @Query("UPDATE products SET active = 0 WHERE id = :id") suspend fun archiveProduct(id: Long)
    @Query("UPDATE products SET stock = stock + :delta WHERE id = :id") suspend fun changeStock(id: Long, delta: Double)
    @Query("SELECT * FROM customers ORDER BY name") fun observeCustomers(): Flow<List<Customer>>
    @Insert suspend fun insertCustomer(customer: Customer): Long
    @Query("SELECT * FROM sales ORDER BY createdAt DESC") fun observeSales(): Flow<List<Sale>>
    @Insert suspend fun insertSale(sale: Sale): Long
    @Insert suspend fun insertItems(items: List<SaleItem>)
    @Insert suspend fun insertPayments(payments: List<Payment>)
    @Query("SELECT * FROM cash_sessions WHERE isOpen = 1 ORDER BY openedAt DESC LIMIT 1") fun observeOpenCash(): Flow<CashSession?>
    @Query("SELECT * FROM cash_sessions WHERE isOpen = 1 ORDER BY openedAt DESC LIMIT 1") suspend fun openCash(): CashSession?
    @Insert suspend fun insertCashSession(session: CashSession): Long
    @Update suspend fun updateCashSession(session: CashSession)
    @Insert suspend fun insertCashMovement(movement: CashMovement)
    @Query("SELECT * FROM cash_movements WHERE sessionId = :sessionId") suspend fun cashMovements(sessionId: Long): List<CashMovement>
    @Insert suspend fun insertFinancial(entry: FinancialEntry)
    @Query("SELECT * FROM financial_entries ORDER BY createdAt DESC") fun observeFinancial(): Flow<List<FinancialEntry>>
}

@Database(
    entities = [Product::class, Customer::class, Sale::class, SaleItem::class, Payment::class, CashSession::class, CashMovement::class, FinancialEntry::class],
    version = 2, exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun dao(): PdvDao
    companion object {
        private val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE products ADD COLUMN photoUri TEXT")
            }
        }
        @Volatile private var INSTANCE: AppDatabase? = null
        fun get(context: Context): AppDatabase = INSTANCE ?: synchronized(this) {
            INSTANCE ?: Room.databaseBuilder(context.applicationContext, AppDatabase::class.java, "pdv-local.db")
                .addMigrations(MIGRATION_1_2).build().also { INSTANCE = it }
        }
    }
}
