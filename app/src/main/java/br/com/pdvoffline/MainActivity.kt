Kotlin

package br.com.pdvoffline

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import br.com.pdvoffline.data.*
import kotlinx.coroutines.launch
import java.text.NumberFormat
import java.util.Locale

private val Purple = Color(0xFF5B57F2)
private val Page = Color(0xFFF4F5FB)
private val brl = NumberFormat.getCurrencyInstance(Locale("pt", "BR"))

private fun money(cents: Long): String =
    brl.format(cents / 100.0)

private fun cents(value: String): Long =
    ((value.replace(",", ".").toDoubleOrNull() ?: 0.0) * 100).toLong()

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val db = (application as PdvApplication).database
        setContent {
            PdvApp(db)
        }
    }
}

@Composable
fun PdvApp(db: AppDatabase) {
    val dao = db.dao()
    val scope = rememberCoroutineScope()

    val products by dao.observeProducts().collectAsStateWithLifecycle(emptyList())
    val customers by dao.observeCustomers().collectAsStateWithLifecycle(emptyList())
    val sales by dao.observeSales().collectAsStateWithLifecycle(emptyList())
    val cash by dao.observeOpenCash().collectAsStateWithLifecycle(null)
    val finance by dao.observeFinancial().collectAsStateWithLifecycle(emptyList())

    val repo = remember { PdvRepository(db) }

    var tab by remember { mutableStateOf("Início") }
    var showProduct by remember { mutableStateOf(false) }
    var showCustomer by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf("") }

    var cart by remember { mutableStateOf(listOf<Pair<Product, Double>>()) }
    var query by remember { mutableStateOf("") }

    var cashValue by remember { mutableStateOf("") }
    var movementValue by remember { mutableStateOf("") }
    var movementDesc by remember { mutableStateOf("") }

    var discount by remember { mutableStateOf("0") }
    var paymentMethod by remember { mutableStateOf("DINHEIRO") }
    var received by remember { mutableStateOf("") }
    var dark by remember { mutableStateOf(false) }

    MaterialTheme(
        colorScheme = if (dark) {
            darkColorScheme(primary = Color(0xFF9B97FF))
        } else {
            lightColorScheme(
                primary = Purple,
                background = Page,
                surface = Color.White
            )
        }
    ) {
        Scaffold(
            containerColor = MaterialTheme.colorScheme.background,
            bottomBar = {
                NavigationBar(
                    containerColor = MaterialTheme.colorScheme.surface
                ) {
                    listOf(
                        "Início",
                        "Catálogo",
                        "Vender",
                        "Financeiro",
                        "Mais"
                    ).forEach { item ->
                        NavigationBarItem(
                            selected = tab == item,
                            onClick = { tab = item },
                            icon = {
                                Icon(
                                    imageVector = when (item) {
                                        "Início" -> Icons.Default.Home
                                        "Catálogo" -> Icons.Default.Inventory
                                        "Vender" -> Icons.Default.PointOfSale
                                        "Financeiro" -> Icons.Default.AccountBalanceWallet
                                        else -> Icons.Default.Menu
                                    },
                                    contentDescription = item,
                                    tint = if (item == "Vender") Color.White else Purple,
                                    modifier = if (item == "Vender") {
                                        Modifier.size(25.dp)
                                    } else {
                                        Modifier
                                    }
                                )
                            },
                            label = { Text(item) },
                            colors = NavigationBarItemDefaults.colors(
                                indicatorColor = if (item == "Vender") {
                                    Purple
                                } else {
                                    Purple.copy(alpha = 0.12f)
                                }
                            )
                        )
                    }
                }
            }
        ) { padding ->
            Column(
                Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .padding(horizontal = 16.dp)
            ) {
                when (tab) {
                    "Início" -> HomeScreen(
                        products.size,
                        sales,
                        cash != null,
                        onGo = { tab = it }
                    )

                    "Catálogo" -> CatalogScreen(
                        products,
                        query,
                        { query = it },
                        { showProduct = true },
                        onDelete = { id ->
                            scope.launch {
                                dao.archiveProduct(id)
                            }
                        }
                    )

                    "Vender" -> SaleScreen(
                        products = products,
                        customers = customers,
                        cart = cart,
                        onCart = { cart = it },
                        query = query,
                        onQuery = { query = it },
                        discount = discount,
                        onDiscount = { discount = it },
                        method = paymentMethod,
                        onMethod = { paymentMethod = it },
                        received = received,
                        onReceived = { received = it },
                        onFinish = {
                            scope.launch {
                                try {
                                    val gross = cart.sumOf { (p, q) ->
                                        (p.salePrice * q).toLong()
                                    }

                                    val discountValue = cents(discount)

                                    require(discountValue >= 0 && discountValue < gross) {
                                        "O desconto deve ser menor que o valor da venda."
                                    }

                                    val total = gross - discountValue

                                    if (paymentMethod == "DINHEIRO") {
                                        require(cents(received) >= total) {
                                            "O valor recebido é insuficiente."
                                        }
                                    }

                                    val payments = listOf(paymentMethod to total)

                                    val saleId = repo.finishSale(
                                        cart,
                                        payments,
                                        null,
                                        discountValue
                                    )

                                    cart = emptyList()
                                    received = ""
                                    discount = "0"

                                    message = "Venda #$saleId finalizada com sucesso."
                                } catch (e: Exception) {
                                    message = e.message
                                        ?: "Não foi possível finalizar."
                                }
                            }
                        },
                        onAdd = { p ->
                            cart = if (cart.any { it.first.id == p.id }) {
                                cart.map {
                                    if (it.first.id == p.id) {
                                        it.first to (it.second + 1)
                                    } else {
                                        it
                                    }
                                }
                            } else {
                                cart + (p to 1.0)
                            }
                        }
                    )

                    "Financeiro" -> FinanceScreen(
                        finance,
                        onAddExpense = { desc, amount ->
                            scope.launch {
                                runCatching {
                                    dao.insertFinancial(
                                        FinancialEntry(
                                            "DESPESA",
                                            desc,
                                            cents(amount),
                                            paid = true
                                        )
                                    )
                                }.onSuccess {
                                    message = "Despesa registrada."
                                }.onFailure {
                                    message = it.message ?: "Erro."
                                }
                            }
                        }
                    )

                    else -> MoreScreen(
                        dark,
                        { dark = it },
                        cash,
                        cashValue,
                        { cashValue = it },
                        movementValue,
                        { movementValue = it },
                        movementDesc,
                        { movementDesc = it },
                        onOpen = {
                            scope.launch {
                                runCatching {
                                    repo.openCash(cents(cashValue))
                                }.onSuccess {
                                    message = "Caixa aberto."
                                }.onFailure {
                                    message = it.message ?: "Erro."
                                }
                            }
                        },
                        onMove = { type ->
                            scope.launch {
                                runCatching {
                                    repo.cashMovement(
                                        type = type,
                                        amount = cents(movementValue),
                                        description = movementDesc
                                    )
                                }.onSuccess {
                                    message = "Movimentação registrada."
                                    movementValue = ""
                                    movementDesc = ""
                                }.onFailure {
                                    message = it.message ?: "Erro."
                                }
                            }
                        },
                        onClose = {
                            scope.launch {
                                runCatching {
                                    repo.closeCash(cents(movementValue))
                                }.onSuccess {
                                    message = "Caixa fechado."
                                    movementValue = ""
                                    movementDesc = ""
                                }.onFailure {
                                    message = it.message ?: "Erro."
                                }
                            }
                        },
                        onCustomer = { showCustomer = true }
                    )
                }
            }
        }

        if (showProduct) {
            ProductDialog(
                onDismiss = { showProduct = false },
                onSave = { p ->
                    scope.launch {
                        runCatching {
                            dao.insertProduct(p)
                        }.onSuccess {
                            message = "Produto cadastrado."
                        }.onFailure {
                            message = "Código já utilizado ou dados inválidos."
                        }
                    }
                    showProduct = false
                }
            )
        }

        if (showCustomer) {
            CustomerDialog(
                onDismiss = { showCustomer = false },
                onSave = { c ->
                    scope.launch {
                        runCatching {
                            dao.insertCustomer(c)
                        }.onSuccess {
                            message = "Cliente cadastrado."
                        }.onFailure {
                            message = "Não foi possível cadastrar."
                        }
                    }
                    showCustomer = false
                }
            )
        }

        if (message.isNotBlank()) {
            AlertDialog(
                onDismissRequest = { message = "" },
                confirmButton = {
                    TextButton(onClick = { message = "" }) {
                        Text("OK")
                    }
                },
                title = {
                    Text("Meu PDV")
                },
                text = {
                    Text(message)
                }
            )
        }
    }
}

@Composable
private fun PageTitle(
    title: String,
    subtitle: String? = null
) {
    Spacer(Modifier.height(18.dp))

    Text(
        title,
        style = MaterialTheme.typography.headlineSmall,
        fontWeight = FontWeight.Bold
    )

    if (subtitle != null) {
        Text(subtitle, color = Color.Gray)
    }

    Spacer(Modifier.height(16.dp))
}

@Composable
private fun CardBox(
    content: @Composable ColumnScope.() -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = 12.dp),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(
            Modifier.padding(16.dp),
            content = content
        )
    }
}

@Composable
private fun HomeScreen(
    productCount: Int,
    sales: List<Sale>,
    cashOpen: Boolean,
    onGo: (String) -> Unit
) {
    val today = sales.filter {
        it.createdAt >= System.currentTimeMillis() - 24 * 60 * 60 * 1000
    }

    val total = today.sumOf { it.total }

    LazyColumn {
        item {
            PageTitle("Olá! 👋", "Resumo da sua loja")

            CardBox {
                Text("Vendas de hoje", color = Color.Gray)

                Text(
                    money(total),
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.Bold
                )

                Spacer(Modifier.height(8.dp))

                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text("${today.size} vendas")

                    Text(
                        "Ticket médio: ${
                            money(
                                if (today.isEmpty()) 0L
                                else total / today.size
                            )
                        }"
                    )
                }

                Text(
                    if (cashOpen) "● Caixa aberto" else "○ Caixa fechado",
                    color = if (cashOpen) Color(0xFF168653) else Color.Gray
                )
            }
        }

        item {
            Text("Acesso rápido", fontWeight = FontWeight.Bold)

            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                listOf("Vender", "Catálogo").forEach { label ->
                    Button(
                        onClick = { onGo(label) },
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(14.dp)
                    ) {
                        Text(label)
                    }
                }
            }

            Spacer(Modifier.height(8.dp))

            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedButton(
                    onClick = { onGo("Mais") },
                    modifier = Modifier.weight(1f)
                ) {
                    Text("Caixa")
                }

                OutlinedButton(
                    onClick = { onGo("Financeiro") },
                    modifier = Modifier.weight(1f)
                ) {
                    Text("Financeiro")
                }
            }
        }

        item {
            CardBox {
                Text("Catálogo", fontWeight = FontWeight.Bold)
                Text("$productCount produtos cadastrados")

                Button(onClick = { onGo("Catálogo") }) {
                    Text("Gerenciar produtos")
                }
            }
        }

        item {
            Text("Últimas vendas", fontWeight = FontWeight.Bold)
        }

        items(sales.take(5)) { sale ->
            CardBox {
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text("Venda #${sale.id}")
                    Text(
                        money(sale.total),
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }
    }
}

@Composable
private fun CatalogScreen(
    products: List<Product>,
    query: String,
    onQuery: (String) -> Unit,
    onAdd: () -> Unit,
    onDelete: (Long) -> Unit
) {
    Column {
        PageTitle("Catálogo", "${products.size} itens cadastrados")

        OutlinedTextField(
            value = query,
            onValueChange = onQuery,
            modifier = Modifier.fillMaxWidth(),
            label = { Text("Buscar nome ou código") },
            leadingIcon = {
                Icon(Icons.Default.Search, null)
            },
            singleLine = true
        )

        Spacer(Modifier.height(10.dp))

        Button(
            onClick = onAdd,
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(14.dp)
        ) {
            Icon(Icons.Default.Add, null)
            Spacer(Modifier.width(8.dp))
            Text("Adicionar produto")
        }

        LazyColumn {
            items(
                products.filter {
                    it.name.contains(query, true) ||
                        it.code.contains(query, true) ||
                        it.barcode.contains(query, true)
                }
            ) { p ->
                CardBox {
                    Row(
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(Modifier.weight(1f)) {
                            Text(
                                p.name,
                                fontWeight = FontWeight.Bold
                            )

                            Text(
                                "Código: ${p.code} • Estoque: ${p.stock}",
                                color = Color.Gray
                            )

                            Text(
                                money(p.salePrice),
                                color = Purple,
                                fontWeight = FontWeight.Bold
                            )
                        }

                        IconButton(
                            onClick = { onDelete(p.id) }
                        ) {
                            Icon(
                                Icons.Default.Delete,
                                contentDescription = "Excluir"
                            )
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SaleScreen(
    products: List<Product>,
    customers: List<Customer>,
    cart: List<Pair<Product, Double>>,
    onCart: (List<Pair<Product, Double>>) -> Unit,
    query: String,
    onQuery: (String) -> Unit,
    discount: String,
    onDiscount: (String) -> Unit,
    method: String,
    onMethod: (String) -> Unit,
    received: String,
    onReceived: (String) -> Unit,
    onFinish: () -> Unit,
    onAdd: (Product) -> Unit
) {
    val total = (
        cart.sumOf { (p, q) ->
            (p.salePrice * q).toLong()
        } - cents(discount)
    ).coerceAtLeast(0L)

    Column {
        PageTitle("Nova venda", "Adicione itens ao carrinho")

        OutlinedTextField(
            value = query,
            onValueChange = onQuery,
            modifier = Modifier.fillMaxWidth(),
            label = { Text("Pesquisar produto") },
            leadingIcon = {
                Icon(Icons.Default.Search, null)
            },
            singleLine = true
        )

        LazyColumn(
            Modifier.weight(1f)
        ) {
            items(
                products.filter {
                    it.name.contains(query, true) ||
                        it.code.contains(query, true) ||
                        it.barcode.contains(query, true)
                }
            ) { p ->
                CardBox {
                    Row(
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(Modifier.weight(1f)) {
                            Text(
                                p.name,
                                fontWeight = FontWeight.SemiBold
                            )

                            Text(
                                "${money(p.salePrice)} • Estoque ${p.stock}",
                                color = Color.Gray
                            )
                        }

                        IconButton(
                            onClick = { onAdd(p) }
                        ) {
                            Icon(
                                Icons.Default.AddCircle,
                                null,
                                tint = Purple
                            )
                        }
                    }
                }
            }
        }

        CardBox {
            Text(
                "Carrinho (${cart.size} itens)",
                fontWeight = FontWeight.Bold
            )

            cart.forEach { (p, q) ->
                Row(
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        "${p.name} × ${q.toInt()}",
                        Modifier.weight(1f)
                    )

                    Text(
                        money((p.salePrice * q).toLong())
                    )

                    IconButton(
                        onClick = {
                            onCart(
                                cart.filterNot {
                                    it.first.id == p.id
                                }
                            )
                        }
                    ) {
                        Icon(Icons.Default.Close, null)
                    }
                }
            }

            OutlinedTextField(
                value = discount,
                onValueChange = onDiscount,
                modifier = Modifier.fillMaxWidth(),
                label = { Text("Desconto em R$") },
                singleLine = true
            )

            Text(
                "Total: ${money(total)}",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold
            )

            var expanded by remember { mutableStateOf(false) }

            ExposedDropdownMenuBox(
                expanded = expanded,
                onExpandedChange = {
                    expanded = it
                }
            ) {
                OutlinedTextField(
                    value = method,
                    onValueChange = {},
                    modifier = Modifier
                        .menuAnchor()
                        .fillMaxWidth(),
                    readOnly = true,
                    label = {
                        Text("Forma de pagamento")
                    },
                    trailingIcon = {
                        ExposedDropdownMenuDefaults.TrailingIcon(expanded)
                    }
                )

                ExposedDropdownMenu(
                    expanded = expanded,
                    onDismissRequest = {
                        expanded = false
                    }
                ) {
                    listOf(
                        "DINHEIRO",
                        "PIX",
                        "CARTAO_CREDITO",
                        "CARTAO_DEBITO",
                        "FIADO"
                    ).forEach { option ->
                        DropdownMenuItem(
                            text = {
                                Text(option.replace("_", " "))
                            },
                            onClick = {
                                onMethod(option)
                                expanded = false
                            }
                        )
                    }
                }
            }

            if (method == "DINHEIRO") {
                OutlinedTextField(
                    value = received,
                    onValueChange = onReceived,
                    modifier = Modifier.fillMaxWidth(),
                    label = {
                        Text("Valor recebido (R$)")
                    },
                    singleLine = true
                )

                val change = cents(received) - total

                Text(
                    if (change >= 0) {
                        "Troco: ${money(change)}"
                    } else {
                        "Faltam: ${money(-change)}"
                    },
                    color = if (change >= 0) {
                        Color(0xFF168653)
                    } else {
                        Color.Red
                    }
                )
            }

            Button(
                onClick = onFinish,
                enabled = cart.isNotEmpty() &&
                    cents(discount) >= 0 &&
                    cents(discount) < cart.sumOf { (p, q) ->
                        (p.salePrice * q).toLong()
                    } &&
                    (
                        method != "DINHEIRO" ||
                            cents(received) >= total
                    ),
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(14.dp)
            ) {
                Text("Finalizar venda")
            }
        }
    }
}

@Composable
private fun FinanceScreen(
    entries: List<FinancialEntry>,
    onAddExpense: (String, String) -> Unit
) {
    var desc by remember { mutableStateOf("") }
    var amount by remember { mutableStateOf("") }

    Column {
        PageTitle("Financeiro", "Movimentações locais")

        CardBox {
            Text("Saldo registrado", color = Color.Gray)

            val balance = entries
                .filter { it.paid }
                .sumOf {
                    if (it.kind == "RECEITA") {
                        it.amount
                    } else {
                        -it.amount
                    }
                }

            Text(
                money(balance),
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold
            )
        }

        CardBox {
            Text(
                "Registrar despesa",
                fontWeight = FontWeight.Bold
            )

            OutlinedTextField(
                value = desc,
                onValueChange = { desc = it },
                modifier = Modifier.fillMaxWidth(),
                label = { Text("Descrição") }
            )

            OutlinedTextField(
                value = amount,
                onValueChange = { amount = it },
                modifier = Modifier.fillMaxWidth(),
                label = { Text("Valor (R$)") }
            )

            Button(
                onClick = {
                    if (desc.isNotBlank() && cents(amount) > 0) {
                        onAddExpense(desc, amount)
                        desc = ""
                        amount = ""
                    }
                },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Salvar despesa")
            }
        }

        Text(
            "Histórico",
            fontWeight = FontWeight.Bold
        )

        LazyColumn {
            items(entries) { entry ->
                CardBox {
                    Row(
                        Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(entry.description)

                        Text(
                            money(entry.amount),
                            color = if (entry.kind == "RECEITA") {
                                Color(0xFF168653)
                            } else {
                                Color.Red
                            }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun MoreScreen(
    dark: Boolean,
    onDark: (Boolean) -> Unit,
    cash: CashSession?,
    cashValue: String,
    onCashValue: (String) -> Unit,
    movementValue: String,
    onMovementValue: (String) -> Unit,
    movementDesc: String,
    onMovementDesc: (String) -> Unit,
    onOpen: () -> Unit,
    onMove: (String) -> Unit,
    onClose: () -> Unit,
    onCustomer: () -> Unit
) {
    Column {
        PageTitle("Mais", "Configurações e operações")

        CardBox {
            Text(
                "Perfil do proprietário",
                fontWeight = FontWeight.Bold
            )

            Text(
                "Operador principal • Dono",
                color = Color.Gray
            )
        }

        CardBox {
            Text(
                "Controle de caixa",
                fontWeight = FontWeight.Bold
            )

            if (cash == null) {
                Text("Caixa fechado")

                OutlinedTextField(
                    value = cashValue,
                    onValueChange = onCashValue,
                    modifier = Modifier.fillMaxWidth(),
                    label = {
                        Text("Fundo de troco (R$)")
                    }
                )

                Button(
                    onClick = onOpen,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Abrir caixa")
                }
            } else {
                Text(
                    "Caixa aberto desde ${
                        java.text.SimpleDateFormat(
                            "dd/MM HH:mm",
                            Locale("pt", "BR")
                        ).format(cash.openedAt)
                    }"
                )

                OutlinedTextField(
                    value = movementValue,
                    onValueChange = onMovementValue,
                    modifier = Modifier.fillMaxWidth(),
                    label = {
                        Text("Valor (R$)")
                    }
                )

                OutlinedTextField(
                    value = movementDesc,
                    onValueChange = onMovementDesc,
                    modifier = Modifier.fillMaxWidth(),
                    label = {
                        Text("Descrição")
                    }
                )

                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Button(
                        onClick = {
                            onMove("SUPRIMENTO")
                        },
                        modifier = Modifier.weight(1f)
                    ) {
                        Text("Suprimento")
                    }

                    Button(
                        onClick = {
                            onMove("SANGRIA")
                        },
                        modifier = Modifier.weight(1f),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = Color(0xFFB64A4A)
                        )
                    ) {
                        Text("Sangria")
                    }
                }

                OutlinedButton(
                    onClick = onClose,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Fechar caixa (valor contado)")
                }
            }
        }

        CardBox {
            Text(
                "Clientes",
                fontWeight = FontWeight.Bold
            )

            Button(onClick = onCustomer) {
                Text("Cadastrar cliente")
            }
        }

        CardBox {
            Text(
                "Aparência",
                fontWeight = FontWeight.Bold
            )

            Row(
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    "Tema escuro",
                    Modifier.weight(1f)
                )

                Switch(
                    checked = dark,
                    onCheckedChange = onDark
                )
            }
        }

        CardBox {
            Text(
                "Armazenamento",
                fontWeight = FontWeight.Bold
            )

            Text(
                "Os dados são mantidos no banco SQLite local deste aparelho."
            )
        }
    }
}

@Composable
private fun ProductDialog(
    onDismiss: () -> Unit,
    onSave: (Product) -> Unit
) {
    var name by remember { mutableStateOf("") }
    var code by remember { mutableStateOf("") }
    var price by remember { mutableStateOf("") }
    var cost by remember { mutableStateOf("") }
    var stock by remember { mutableStateOf("0") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text("Novo produto")
        },
        text = {
            Column(
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                OutlinedTextField(
                    name,
                    { name = it },
                    label = { Text("Nome") }
                )

                OutlinedTextField(
                    code,
                    { code = it },
                    label = { Text("Código único") }
                )

                OutlinedTextField(
                    price,
                    { price = it },
                    label = { Text("Preço de venda (R$)") }
                )

                OutlinedTextField(
                    cost,
                    { cost = it },
                    label = { Text("Preço de custo (R$)") }
                )

                OutlinedTextField(
                    stock,
                    { stock = it },
                    label = { Text("Estoque inicial") }
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    if (
                        name.isNotBlank() &&
                        code.isNotBlank() &&
                        cents(price) > 0
                    ) {
                        onSave(
                            Product(
                                name = name.trim(),
                                code = code.trim(),
                                salePrice = cents(price),
                                costPrice = cents(cost),
                                stock = stock.toDoubleOrNull() ?: 0.0
                            )
                        )
                    }
                }
            ) {
                Text("Salvar")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancelar")
            }
        }
    )
}

@Composable
private fun CustomerDialog(
    onDismiss: () -> Unit,
    onSave: (Customer) -> Unit
) {
    var name by remember { mutableStateOf("") }
    var phone by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text("Novo cliente")
        },
        text = {
            Column {
                OutlinedTextField(
                    name,
                    { name = it },
                    label = { Text("Nome") }
                )

                OutlinedTextField(
                    phone,
                    { phone = it },
                    label = { Text("Telefone") }
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    if (name.isNotBlank()) {
                        onSave(
                            Customer(
                                name = name.trim(),
                                phone = phone.trim()
                            )
                        )
                    }
                }
            ) {
                Text("Salvar")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancelar")
            }
        }
    )
}
