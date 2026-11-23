package br.com.pdvoffline.data

import android.content.Context
import androidx.room.*
import androidx.room.withTransaction
import kotlinx.coroutines.flow.Flow

@Entity(tableName = "products", indices = [Index(value = ["code"], unique = true)])
data class Product(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val code: String,
    val barcode: String = "",
    val category: String = "",
    val description: String = "",
    val salePrice: Long, // centavos
    val costPrice: Long = 0,
    val stock: Double = 0.0,
    val minStock: Double = 0.0,
    val isService: Boolean = false,
    val active: Boolean = true
)

@Entity(tableName = "customers")
data class Customer(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val phone: String = "",
    val cpf: String = "",
    val address: String = "",
    val notes: String = ""
)

@Entity(tableName = "sales")
data class Sale(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val createdAt: Long = System.currentTimeMillis(),
    val customerId: Long? = null,
    val total: Long,
    val discount: Long = 0,
    val status: String = "FINALIZADA"
)

@Entity(tableName = "sale_items")
data class SaleItem(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val saleId: Long,
    val productId: Long,
    val productName: String,
    val quantity: Double,
    val unitPrice: Long,
    val unitCost: Long,
    val lineTotal: Long
)

@Entity(tableName = "payments")
data class Payment(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val saleId: Long,
    val method: String,
    val amount: Long,
    val createdAt: Long = System.currentTimeMillis()
)

@Entity(tableName = "cash_sessions")
data class CashSession(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val openedAt: Long = System.currentTimeMillis(),
    val closedAt: Long? = null,
    val openingFloat: Long,
    val closingCount: Long? = null,
    val operator: String = "Proprietário",
    val isOpen: Boolean = true
)

@Entity(tableName = "cash_movements")
data class CashMovement(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val sessionId: Long,
    val type: String, // VENDA_DINHEIRO, SUPRIMENTO, SANGRIA
    val amount: Long,
    val description: String,
    val createdAt: Long = System.currentTimeMillis()
)

@Entity(tableName = "financial_entries")
data class FinancialEntry(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val kind: String, // RECEITA, DESPESA, A_RECEBER, A_PAGAR
    val description: String,
    val amount: Long,
    val dueAt: Long? = null,
    val paid: Boolean = false,
    val customerId: Long? = null,
    val createdAt: Long = System.currentTimeMillis()
)

@Dao
interface PdvDao {
    @Query("SELECT * FROM products WHERE active = 1 ORDER BY name")
    fun observeProducts(): Flow<List<Product>>

    @Query("SELECT * FROM products WHERE active = 1 AND (name LIKE '%' || :q || '%' OR code LIKE '%' || :q || '%' OR barcode LIKE '%' || :q || '%') ORDER BY name")
    fun searchProducts(q: String): Flow<List<Product>>

    @Query("SELECT * FROM products WHERE id = :id")
    suspend fun product(id: Long): Product?

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertProduct(product: Product): Long

    @Update
    suspend fun updateProduct(product: Product)

    @Query("UPDATE products SET active = 0 WHERE id = :id")
    suspend fun archiveProduct(id: Long)

    @Query("UPDATE products SET stock = stock + :delta WHERE id = :id")
    suspend fun changeStock(id: Long, delta: Double)

    @Query("SELECT * FROM customers ORDER BY name")
    fun observeCustomers(): Flow<List<Customer>>

    @Insert
    suspend fun insertCustomer(customer: Customer): Long

    @Update
    suspend fun updateCustomer(customer: Customer)

    @Query("SELECT * FROM sales ORDER BY createdAt DESC")
    fun observeSales(): Flow<List<Sale>>

    @Query("SELECT * FROM sales WHERE createdAt >= :from AND createdAt < :to AND status = 'FINALIZADA'")
    suspend fun salesBetween(from: Long, to: Long): List<Sale>

    @Query("SELECT * FROM sale_items WHERE saleId = :saleId")
    suspend fun itemsForSale(saleId: Long): List<SaleItem>

    @Insert
    suspend fun insertSale(sale: Sale): Long

    @Insert
    suspend fun insertItems(items: List<SaleItem>)

    @Insert
    suspend fun insertPayments(payments: List<Payment>)

    @Query("SELECT * FROM cash_sessions WHERE isOpen = 1 ORDER BY openedAt DESC LIMIT 1")
    fun observeOpenCash(): Flow<CashSession?>

    @Query("SELECT * FROM cash_sessions WHERE isOpen = 1 ORDER BY openedAt DESC LIMIT 1")
    suspend fun openCash(): CashSession?

    @Insert
    suspend fun insertCashSession(session: CashSession): Long

    @Update
    suspend fun updateCashSession(session: CashSession)

    @Insert
    suspend fun insertCashMovement(movement: CashMovement)

    @Query("SELECT * FROM cash_movements WHERE sessionId = :sessionId ORDER BY createdAt DESC")
    fun observeCashMovements(sessionId: Long): Flow<List<CashMovement>>

    @Insert
    suspend fun insertFinancial(entry: FinancialEntry)

    @Query("SELECT * FROM financial_entries ORDER BY createdAt DESC")
    fun observeFinancial(): Flow<List<FinancialEntry>>

    @Query("SELECT COALESCE(SUM(amount), 0) FROM payments WHERE method = 'FIADO'")
    fun unusedDebtAggregate(): Flow<Long>
}

@Database(
    entities = [Product::class, Customer::class, Sale::class, SaleItem::class, Payment::class,
        CashSession::class, CashMovement::class, FinancialEntry::class],
    version = 1,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun dao(): PdvDao

    companion object {
        @Volatile private var INSTANCE: AppDatabase? = null
        fun get(context: Context): AppDatabase = INSTANCE ?: synchronized(this) {
            INSTANCE ?: Room.databaseBuilder(
                context.applicationContext, AppDatabase::class.java, "pdv-local.db"
            ).build().also { INSTANCE = it }
        }
    }
}
