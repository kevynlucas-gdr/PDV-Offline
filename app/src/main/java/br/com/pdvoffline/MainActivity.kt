
package br.com.pdvoffline

import android.content.Intent
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.verticalScroll
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.core.view.WindowCompat
import br.com.pdvoffline.data.*
import kotlinx.coroutines.launch
import java.text.NumberFormat
import java.util.Locale

private val brl = NumberFormat.getCurrencyInstance(Locale("pt", "BR"))
private fun money(v: Long) = brl.format(v / 100.0)

private fun cents(value: String): Long {
    val raw = value.trim().replace("R$", "", true).replace(" ", "")
    if (raw.isBlank()) return 0
    val normalized = if (raw.contains(",")) raw.replace(".", "").replace(",", ".") else raw
    return runCatching {
        normalized.toBigDecimal().movePointRight(2)
            .setScale(0, java.math.RoundingMode.HALF_UP).longValueExact()
    }.getOrDefault(0)
}

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val db = (application as PdvApplication).database
        setContent { PdvApp(db) }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun PdvApp(db: AppDatabase) {
    val context = LocalContext.current
    val dao = db.dao()
    val repo = remember { PdvRepository(db) }
    val scope = rememberCoroutineScope()

    val products by dao.observeProducts().collectAsStateWithLifecycle(emptyList())
    val sales by dao.observeSales().collectAsStateWithLifecycle(emptyList())
    val cash by dao.observeOpenCash().collectAsStateWithLifecycle(null)
    val entries by dao.observeFinancial().collectAsStateWithLifecycle(emptyList())

    val dynamic = android.os.Build.VERSION.SDK_INT >= 31
    val dark = androidx.compose.foundation.isSystemInDarkTheme()

    val scheme = when {
        dynamic && dark -> dynamicDarkColorScheme(context)
        dynamic -> dynamicLightColorScheme(context)
        dark -> darkColorScheme()
        else -> lightColorScheme()
    }

    // Corrige a cor da barra de status e da barra de navegação.
    SideEffect {
        val window = (context as? ComponentActivity)?.window

        window?.let {
            it.statusBarColor = scheme.background.toArgb()
            it.navigationBarColor = scheme.background.toArgb()

            val controller = WindowCompat.getInsetsController(it, it.decorView)
            controller.isAppearanceLightStatusBars = !dark
            controller.isAppearanceLightNavigationBars = !dark
        }
    }

    var tab by remember { mutableStateOf("Início") }
    var subPage by remember { mutableStateOf<String?>(null) }
    BackHandler(enabled = subPage != null) { subPage = null }

    var search by remember { mutableStateOf("") }
    var showProduct by remember { mutableStateOf(false) }
    var cart by remember { mutableStateOf(listOf<Pair<Product, Int>>()) }
    var payment by remember { mutableStateOf("DINHEIRO") }
    var received by remember { mutableStateOf("") }
    var discount by remember { mutableStateOf("0") }
    var feedback by remember { mutableStateOf("") }
    var amount by remember { mutableStateOf("") }
    var description by remember { mutableStateOf("") }
    var expenseName by remember { mutableStateOf("") }
    var expenseValue by remember { mutableStateOf("") }

    val snackbar = remember { SnackbarHostState() }

    LaunchedEffect(feedback) {
        if (feedback.isNotBlank()) {
            snackbar.showSnackbar(feedback)
            feedback = ""
        }
    }

    MaterialTheme(colorScheme = scheme) {
        Scaffold(
            snackbarHost = { SnackbarHost(snackbar) },
            bottomBar = {
                NavigationBar {
                    listOf("Início", "Catálogo", "Vender", "Financeiro", "Mais").forEach { label ->
                        NavigationBarItem(
                            selected = (subPage ?: tab) == label ||
                                (label == "Mais" && subPage == "Caixa"),
                            onClick = {
                                subPage = null
                                tab = label
                            },
                            icon = {
                                Icon(
                                    when (label) {
                                        "Início" -> Icons.Rounded.Home
                                        "Catálogo" -> Icons.Rounded.Inventory2
                                        "Vender" -> Icons.Rounded.PointOfSale
                                        "Financeiro" -> Icons.Rounded.AccountBalanceWallet
                                        else -> Icons.Rounded.MoreHoriz
                                    },
                                    contentDescription = label
                                )
                            },
                            label = { Text(label, maxLines = 1) }
                        )
                    }
                }
            }
        ) { padding ->
            Column(
                Modifier.fillMaxSize()
                    .padding(padding)
                    .padding(horizontal = 16.dp)
            ) {
                when (subPage ?: tab) {
                    "Início" -> Home(
                        products.size,
                        sales,
                        cash != null,
                        onSell = { tab = "Vender"; subPage = null },
                        onCatalog = { tab = "Catálogo"; subPage = null }
                    )

                    "Catálogo" -> Catalog(
                        products,
                        search,
                        { search = it },
                        { showProduct = true },
                        { id ->
                            scope.launch {
                                dao.archiveProduct(id)
                                feedback = "Produto arquivado."
                            }
                        }
                    )

                    "Vender" -> Sell(
                        products, search, { search = it },
                        cart, { cart = it },
                        discount, { discount = it },
                        payment, { payment = it },
                        received, { received = it }
                    ) {
                        scope.launch {
                            try {
                                val gross = cart.sumOf { it.first.salePrice * it.second }
                                val disc = cents(discount).coerceIn(0, gross)
                                val total = gross - disc

                                if (payment == "DINHEIRO" && cents(received) < total) {
                                    error("O valor recebido é insuficiente.")
                                }

                                val id = repo.finishSale(
                                    cart.map { it.first to it.second.toDouble() },
                                    listOf(payment to total),
                                    null,
                                    disc
                                )

                                cart = emptyList()
                                received = ""
                                discount = "0"
                                feedback = "Venda #$id concluída."
                            } catch (e: Exception) {
                                feedback = e.message ?: "Não foi possível concluir a venda."
                            }
                        }
                    }

                    "Financeiro" -> Finance(
                        entries,
                        expenseName,
                        { expenseName = it },
                        expenseValue,
                        { expenseValue = it }
                    ) {
                        val value = cents(expenseValue)

                        if (expenseName.isBlank() || value <= 0) {
                            feedback = "Informe descrição e valor válidos."
                        } else {
                            scope.launch {
                                dao.insertFinancial(
                                    FinancialEntry(
                                        kind = "DESPESA",
                                        description = expenseName.trim(),
                                        amount = value,
                                        paid = true
                                    )
                                )
                                expenseName = ""
                                expenseValue = ""
                                feedback = "Despesa registrada."
                            }
                        }
                    }

                    "Mais", "Caixa" -> Cash(
                        cash != null,
                        amount,
                        { amount = it },
                        description,
                        { description = it },
                        onOpen = {
                            scope.launch {
                                runCatching {
                                    repo.openCash(cents(amount))
                                    amount = ""
                                    feedback = "Caixa aberto."
                                }.onFailure {
                                    feedback = it.message ?: "Erro ao abrir caixa."
                                }
                            }
                        },
                        onMove = { type ->
                            scope.launch {
                                runCatching {
                                    repo.cashMovement(type, cents(amount), description)
                                    amount = ""
                                    description = ""
                                    feedback = "Movimentação registrada."
                                }.onFailure {
                                    feedback = it.message ?: "Erro na movimentação."
                                }
                            }
                        },
                        onClose = {
                            scope.launch {
                                runCatching {
                                    val result = repo.closeCash(cents(amount))
                                    amount = ""
                                    feedback = "Caixa fechado. Diferença: ${money(result.second)}"
                                }.onFailure {
                                    feedback = it.message ?: "Erro ao fechar caixa."
                                }
                            }
                        }
                    )
                }
            }
        }

        if (showProduct) {
            ProductDialog(onDismiss = { showProduct = false }) {
                    name, code, price, cost, stock, photo ->
                scope.launch {
                    try {
                        dao.insertProduct(
                            Product(
                                name = name,
                                code = code,
                                salePrice = cents(price),
                                costPrice = cents(cost),
                                stock = stock.toDoubleOrNull() ?: 0.0,
                                photoUri = photo
                            )
                        )
                        showProduct = false
                        feedback = "Produto cadastrado."
                    } catch (e: Exception) {
                        feedback = "Não foi possível salvar. Verifique se o código já existe."
                    }
                }
            }
        }
    }
}

@Composable
private fun PageTitle(title: String, subtitle: String? = null) {
    Spacer(Modifier.height(18.dp))
    Text(
        title,
        style = MaterialTheme.typography.headlineMedium,
        fontWeight = FontWeight.SemiBold
    )
    if (subtitle != null) {
        Text(
            subtitle,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
    Spacer(Modifier.height(16.dp))
}

@Composable
private fun Home(
    count: Int,
    sales: List<Sale>,
    cashOpen: Boolean,
    onSell: () -> Unit,
    onCatalog: () -> Unit
) {
    val today = sales.filter {
        val a = java.util.Calendar.getInstance()
        val b = java.util.Calendar.getInstance().apply { timeInMillis = it.createdAt }
        a.get(java.util.Calendar.YEAR) == b.get(java.util.Calendar.YEAR) &&
            a.get(java.util.Calendar.DAY_OF_YEAR) == b.get(java.util.Calendar.DAY_OF_YEAR)
    }

    val total = today.sumOf { it.total }

    LazyColumn(contentPadding = PaddingValues(bottom = 20.dp)) {
        item {
            PageTitle("Visão geral", "Resumo das operações de hoje")
        }

        item {
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(20.dp)) {
                    Text("Vendas de hoje", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text(
                        money(total),
                        style = MaterialTheme.typography.headlineLarge,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(Modifier.height(12.dp))
                    Row(
                        Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text("${today.size} vendas")
                        Text(if (cashOpen) "Caixa aberto" else "Caixa fechado")
                    }
                }
            }
        }

        item {
            Spacer(Modifier.height(16.dp))
            Text("Acesso rápido", style = MaterialTheme.typography.titleMedium)
        }

        item {
            Row(
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier.padding(top = 8.dp)
            ) {
                FilledTonalButton(
                    onClick = onSell,
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(Icons.Rounded.PointOfSale, null)
                    Spacer(Modifier.width(6.dp))
                    Text("Nova venda")
                }

                OutlinedButton(
                    onClick = onCatalog,
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(Icons.Rounded.Inventory2, null)
                    Spacer(Modifier.width(6.dp))
                    Text("Catálogo")
                }
            }
        }

        item {
            Spacer(Modifier.height(12.dp))
            Text("$count produtos cadastrados", color = MaterialTheme.colorScheme.onSurfaceVariant)
        }

        item {
            Spacer(Modifier.height(20.dp))
            Text("Vendas recentes", style = MaterialTheme.typography.titleMedium)
        }

        if (today.isEmpty()) {
            item {
                Text(
                    "Nenhuma venda hoje.",
                    Modifier.padding(vertical = 14.dp),
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        items(today.take(8)) { sale ->
            ListItem(
                leadingContent = { Icon(Icons.Rounded.ReceiptLong, null) },
                headlineContent = { Text("Venda #${sale.id}") },
                supportingContent = {
                    Text(
                        java.text.SimpleDateFormat(
                            "dd/MM • HH:mm",
                            Locale("pt", "BR")
                        ).format(java.util.Date(sale.createdAt))
                    )
                },
                trailingContent = {
                    Text(money(sale.total), fontWeight = FontWeight.SemiBold)
                }
            )
            HorizontalDivider()
        }
    }
}

@Composable
private fun ProductPhoto(uri: String?, modifier: Modifier = Modifier) {
    val context = LocalContext.current

    val bitmap = remember(uri) {
        if (uri == null) null else runCatching {
            context.contentResolver.openInputStream(Uri.parse(uri))
                ?.use { BitmapFactory.decodeStream(it)?.asImageBitmap() }
        }.getOrNull()
    }

    if (bitmap != null) {
        Image(bitmap, null, modifier.clip(RoundedCornerShape(12.dp)))
    } else {
        Box(
            modifier.clip(RoundedCornerShape(12.dp))
                .background(MaterialTheme.colorScheme.surfaceContainerHighest),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                Icons.Rounded.Inventory2,
                null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun Catalog(
    products: List<Product>,
    query: String,
    onQuery: (String) -> Unit,
    onAdd: () -> Unit,
    onArchive: (Long) -> Unit
) {
    Column(Modifier.fillMaxSize()) {
        PageTitle("Catálogo", "${products.size} produtos ativos")

        OutlinedTextField(
            query,
            onQuery,
            Modifier.fillMaxWidth(),
            singleLine = true,
            label = { Text("Buscar produto") },
            leadingIcon = { Icon(Icons.Rounded.Search, null) }
        )

        Spacer(Modifier.height(10.dp))

        FilledTonalButton(
            onClick = onAdd,
            modifier = Modifier.fillMaxWidth()
        ) {
            Icon(Icons.Rounded.Add, null)
            Text("Adicionar produto")
        }

        LazyColumn(contentPadding = PaddingValues(vertical = 8.dp)) {
            items(products.filter {
                it.name.contains(query, true) || it.code.contains(query, true)
            }) { product ->
                ListItem(
                    leadingContent = {
                        ProductPhoto(product.photoUri, Modifier.size(52.dp))
                    },
                    headlineContent = {
                        Text(product.name, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    },
                    supportingContent = {
                        Text("${product.code} • Estoque ${product.stock}")
                    },
                    trailingContent = {
                        Column(horizontalAlignment = Alignment.End) {
                            Text(money(product.salePrice), fontWeight = FontWeight.SemiBold)
                            TextButton(onClick = { onArchive(product.id) }) {
                                Text("Arquivar")
                            }
                        }
                    }
                )
                HorizontalDivider()
            }
        }
    }
}

@Composable
private fun ProductDialog(
    onDismiss: () -> Unit,
    onSave: (String, String, String, String, String, String?) -> Unit
) {
    val context = LocalContext.current

    var name by remember { mutableStateOf("") }
    var code by remember { mutableStateOf("") }
    var price by remember { mutableStateOf("") }
    var cost by remember { mutableStateOf("0") }
    var stock by remember { mutableStateOf("0") }
    var photo by remember { mutableStateOf<String?>(null) }

    val picker = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) {
            runCatching {
                context.contentResolver.takePersistableUriPermission(
                    uri,
                    Intent.FLAG_GRANT_READ_URI_PERMISSION
                )
            }
            photo = uri.toString()
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Novo produto") },
        text = {
            Column(
                Modifier.heightIn(max = 520.dp)
                    .verticalScroll(androidx.compose.foundation.rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    ProductPhoto(photo, Modifier.size(72.dp))
                    Spacer(Modifier.width(12.dp))
                    OutlinedButton(onClick = { picker.launch(arrayOf("image/*")) }) {
                        Icon(Icons.Rounded.AddPhotoAlternate, null)
                        Text(if (photo == null) "Adicionar foto" else "Trocar foto")
                    }
                }

                OutlinedTextField(name, { name = it }, label = { Text("Nome") }, singleLine = true)
                OutlinedTextField(code, { code = it }, label = { Text("Código") }, singleLine = true)
                OutlinedTextField(price, { price = it }, label = { Text("Preço de venda") }, singleLine = true)
                OutlinedTextField(cost, { cost = it }, label = { Text("Preço de custo") }, singleLine = true)
                OutlinedTextField(stock, { stock = it }, label = { Text("Estoque inicial") }, singleLine = true)
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    if (name.isNotBlank() && code.isNotBlank() && cents(price) > 0) {
                        onSave(name.trim(), code.trim(), price, cost, stock, photo)
                    }
                }
            ) {
                Text("Salvar")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancelar") }
        }
    )
}

@Composable
private fun Sell(
    products: List<Product>,
    query: String,
    onQuery: (String) -> Unit,
    cart: List<Pair<Product, Int>>,
    onCart: (List<Pair<Product, Int>>) -> Unit,
    discount: String,
    onDiscount: (String) -> Unit,
    payment: String,
    onPayment: (String) -> Unit,
    received: String,
    onReceived: (String) -> Unit,
    onFinish: () -> Unit
) {
    val gross = cart.sumOf { it.first.salePrice * it.second }
    val total = (gross - cents(discount)).coerceAtLeast(0)

    Column(Modifier.fillMaxSize()) {
        PageTitle("Nova venda", "Selecione os produtos para adicionar ao carrinho")

        OutlinedTextField(
            query,
            onQuery,
            Modifier.fillMaxWidth(),
            singleLine = true,
            label = { Text("Buscar produto") },
            leadingIcon = { Icon(Icons.Rounded.Search, null) }
        )

        LazyColumn(
            Modifier.weight(0.34f),
            contentPadding = PaddingValues(vertical = 6.dp)
        ) {
            items(products.filter {
                it.name.contains(query, true) || it.code.contains(query, true)
            }.take(25)) { p ->
                ListItem(
                    modifier = Modifier.clickable {
                        onCart(
                            if (cart.any { it.first.id == p.id }) {
                                cart.map {
                                    if (it.first.id == p.id) it.first to it.second + 1 else it
                                }
                            } else {
                                cart + (p to 1)
                            }
                        )
                    },
                    leadingContent = { ProductPhoto(p.photoUri, Modifier.size(42.dp)) },
                    headlineContent = { Text(p.name, maxLines = 1) },
                    supportingContent = { Text("${money(p.salePrice)} • estoque ${p.stock}") },
                    trailingContent = {
                        Icon(
                            Icons.Rounded.AddCircle,
                            null,
                            tint = MaterialTheme.colorScheme.primary
                        )
                    }
                )
            }
        }

        HorizontalDivider()

        Text(
            "Carrinho (${cart.sumOf { it.second }})",
            Modifier.padding(top = 8.dp),
            style = MaterialTheme.typography.titleMedium
        )

        LazyColumn(
            Modifier.weight(0.22f),
            contentPadding = PaddingValues(vertical = 4.dp)
        ) {
            items(cart) { (p, q) ->
                Row(
                    Modifier.fillMaxWidth().padding(vertical = 2.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        "$q × ${p.name}",
                        Modifier.weight(1f),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(money(p.salePrice * q))

                    IconButton(
                        onClick = {
                            onCart(
                                if (q > 1) {
                                    cart.map {
                                        if (it.first.id == p.id) p to q - 1 else it
                                    }
                                } else {
                                    cart.filterNot { it.first.id == p.id }
                                }
                            )
                        }
                    ) {
                        Icon(Icons.Rounded.RemoveCircleOutline, null)
                    }

                    IconButton(
                        onClick = {
                            onCart(cart.map {
                                if (it.first.id == p.id) p to q + 1 else it
                            })
                        }
                    ) {
                        Icon(Icons.Rounded.AddCircleOutline, null)
                    }
                }
            }
        }

        OutlinedTextField(
            discount,
            onDiscount,
            Modifier.fillMaxWidth(),
            label = { Text("Desconto em reais") },
            singleLine = true
        )

        Text(
            "Total: ${money(total)}",
            Modifier.align(Alignment.End).padding(vertical = 6.dp),
            style = MaterialTheme.typography.titleLarge
        )

        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            listOf(
                "DINHEIRO",
                "PIX",
                "CARTAO_CREDITO",
                "CARTAO_DEBITO",
                "FIADO"
            ).forEach { method ->
                FilterChip(
                    selected = payment == method,
                    onClick = { onPayment(method) },
                    label = {
                        Text(
                            when (method) {
                                "CARTAO_CREDITO" -> "Crédito"
                                "CARTAO_DEBITO" -> "Débito"
                                else -> method.lowercase().replaceFirstChar { it.uppercase() }
                            },
                            fontSize = 10.sp
                        )
                    }
                )
            }
        }

        if (payment == "DINHEIRO") {
            OutlinedTextField(
                received,
                onReceived,
                Modifier.fillMaxWidth(),
                label = { Text("Valor recebido") },
                singleLine = true
            )
        }

        Button(
            onClick = onFinish,
            enabled = cart.isNotEmpty() && total > 0 &&
                (payment != "DINHEIRO" || cents(received) >= total),
            modifier = Modifier.fillMaxWidth().padding(top = 8.dp, bottom = 8.dp)
        ) {
            Text("Finalizar venda")
        }
    }
}

@Composable
private fun Finance(
    entries: List<FinancialEntry>,
    name: String,
    onName: (String) -> Unit,
    value: String,
    onValue: (String) -> Unit,
    onSave: () -> Unit
) {
    Column(Modifier.fillMaxSize()) {
        PageTitle("Financeiro", "Entradas e despesas registradas")

        val incoming = entries.filter { it.paid && it.kind == "RECEITA" }.sumOf { it.amount }
        val outgoing = entries.filter { it.paid && it.kind == "DESPESA" }.sumOf { it.amount }

        Card(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(18.dp)) {
                Text("Saldo registrado", color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(
                    money(incoming - outgoing),
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.Bold
                )
                Text("Entradas ${money(incoming)}   •   Saídas ${money(outgoing)}")
            }
        }

        Spacer(Modifier.height(14.dp))
        Text("Nova despesa", style = MaterialTheme.typography.titleMedium)

        OutlinedTextField(name, onName, Modifier.fillMaxWidth(), label = { Text("Descrição") })
        OutlinedTextField(value, onValue, Modifier.fillMaxWidth(), label = { Text("Valor") })

        Button(onClick = onSave, Modifier.fillMaxWidth()) {
            Text("Registrar despesa")
        }

        Spacer(Modifier.height(12.dp))
        Text("Lançamentos", style = MaterialTheme.typography.titleMedium)

        LazyColumn {
            items(entries) { e ->
                ListItem(
                    headlineContent = { Text(e.description) },
                    supportingContent = { Text(e.kind) },
                    trailingContent = { Text(money(e.amount)) }
                )
                HorizontalDivider()
            }
        }
    }
}

@Composable
private fun Cash(
    isOpen: Boolean,
    value: String,
    onValue: (String) -> Unit,
    description: String,
    onDescription: (String) -> Unit,
    onOpen: () -> Unit,
    onMove: (String) -> Unit,
    onClose: () -> Unit
) {
    Column(Modifier.fillMaxSize()) {
        PageTitle("Caixa", if (isOpen) "Sessão aberta" else "Nenhuma sessão aberta")

        Card(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(18.dp)) {
                Text(
                    if (isOpen) "Caixa aberto" else "Caixa fechado",
                    style = MaterialTheme.typography.titleLarge
                )

                OutlinedTextField(
                    value,
                    onValue,
                    Modifier.fillMaxWidth(),
                    label = {
                        Text(if (isOpen) "Valor da operação ou total contado" else "Fundo inicial")
                    }
                )

                if (isOpen) {
                    OutlinedTextField(
                        description,
                        onDescription,
                        Modifier.fillMaxWidth(),
                        label = { Text("Descrição") }
                    )

                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedButton(
                            onClick = { onMove("SANGRIA") },
                            Modifier.weight(1f)
                        ) {
                            Text("Sangria")
                        }

                        OutlinedButton(
                            onClick = { onMove("SUPRIMENTO") },
                            Modifier.weight(1f)
                        ) {
                            Text("Suprimento")
                        }
                    }

                    Button(onClick = onClose, Modifier.fillMaxWidth()) {
                        Text("Fechar caixa")
                    }
                } else {
                    Button(onClick = onOpen, Modifier.fillMaxWidth()) {
                        Text("Abrir caixa")
                    }
                }
            }
        }
    }
}
