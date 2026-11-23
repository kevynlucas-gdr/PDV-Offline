package br.com.pdvoffline

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import br.com.pdvoffline.data.*
import kotlinx.coroutines.launch
import java.text.NumberFormat
import java.util.Locale

private val Ink = Color(0xFF17172B)
private val Muted = Color(0xFF85859A)
private val Canvas = Color(0xFFF5F5FA)
private val Purple = Color(0xFF5B57F2)
private val Violet = Color(0xFF817CFF)
private val Mint = Color(0xFF25B894)
private val Amber = Color(0xFFFFB454)
private val Rose = Color(0xFFFF718B)
private val White = Color.White
private val brl = NumberFormat.getCurrencyInstance(Locale("pt", "BR"))
private fun money(v: Long) = brl.format(v / 100.0)

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.statusBarColor = android.graphics.Color.rgb(245, 245, 250)
        window.navigationBarColor = android.graphics.Color.WHITE
        @Suppress("DEPRECATION")
        window.decorView.systemUiVisibility =
            android.view.View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR or
            android.view.View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR
        val db = (application as PdvApplication).database
        setContent { PremiumPdv(db) }
    }
}

@Composable
private fun PremiumPdv(db: AppDatabase) {
    val dao = db.dao()
    val repo = remember { PdvRepository(db) }
    val scope = rememberCoroutineScope()
    val products by dao.observeProducts().collectAsStateWithLifecycle(emptyList())
    val sales by dao.observeSales().collectAsStateWithLifecycle(emptyList())
    val cash by dao.observeOpenCash().collectAsStateWithLifecycle(null)
    val finance by dao.observeFinancial().collectAsStateWithLifecycle(emptyList())

    val backStack = remember { mutableStateListOf("Início") }
    val page = backStack.last()
    fun navigate(destination: String) {
        if (backStack.lastOrNull() != destination) backStack.add(destination)
    }
    fun selectTab(destination: String) {
        backStack.clear()
        backStack.add(destination)
    }
    BackHandler(enabled = backStack.size > 1) {
        backStack.removeAt(backStack.lastIndex)
    }
    var query by remember { mutableStateOf("") }
    var showNewProduct by remember { mutableStateOf(false) }
    var toast by remember { mutableStateOf("") }
    var cart by remember { mutableStateOf(listOf<Pair<Product, Int>>()) }
    var payment by remember { mutableStateOf("DINHEIRO") }
    var received by remember { mutableStateOf("") }
    var discount by remember { mutableStateOf("0") }
    var cashValue by remember { mutableStateOf("") }
    var cashDescription by remember { mutableStateOf("") }
    var expenseName by remember { mutableStateOf("") }
    var expenseAmount by remember { mutableStateOf("") }

    MaterialTheme(
        colorScheme = lightColorScheme(
            primary = Purple, onPrimary = White, secondary = Mint,
            background = Canvas, surface = White, onSurface = Ink,
            onBackground = Ink
        ),
        typography = Typography(
            headlineLarge = MaterialTheme.typography.headlineLarge.copy(fontWeight = FontWeight.Bold),
            headlineMedium = MaterialTheme.typography.headlineMedium.copy(fontWeight = FontWeight.Bold),
            titleLarge = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
            titleMedium = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold)
        )
    ) {
        Scaffold(
            containerColor = Canvas,
            bottomBar = {
                NavigationBar(
                    containerColor = White,
                    tonalElevation = 12.dp,
                    modifier = Modifier.height(82.dp)
                ) {
                    val items = listOf(
                        Triple("Início", Icons.Rounded.Home, "Início"),
                        Triple("Catálogo", Icons.Rounded.Inventory2, "Catálogo"),
                        Triple("Vender", Icons.Rounded.PointOfSale, "Vender"),
                        Triple("Financeiro", Icons.Rounded.AccountBalanceWallet, "Financeiro"),
                        Triple("Mais", Icons.Rounded.GridView, "Mais")
                    )
                    items.forEach { (label, icon, key) ->
                        NavigationBarItem(
                            selected = page == key,
                            onClick = { selectTab(key) },
                            icon = {
                                if (key == "Vender") {
                                    Box(
                                        Modifier.size(42.dp).clip(RoundedCornerShape(15.dp))
                                            .background(Brush.linearGradient(listOf(Purple, Violet))),
                                        contentAlignment = Alignment.Center
                                    ) { Icon(icon, null, tint = White, modifier = Modifier.size(23.dp)) }
                                } else Icon(icon, null, modifier = Modifier.size(22.dp))
                            },
                            label = { Text(label, fontSize = 10.sp, fontWeight = if (page == key) FontWeight.Bold else FontWeight.Medium) },
                            colors = NavigationBarItemDefaults.colors(
                                selectedIconColor = Purple, selectedTextColor = Purple,
                                indicatorColor = Color.Transparent, unselectedIconColor = Muted,
                                unselectedTextColor = Muted
                            )
                        )
                    }
                }
            },
            snackbarHost = { if (toast.isNotBlank()) SnackbarHost(remember { SnackbarHostState() }) }
        ) { pad ->
            Column(
                Modifier.fillMaxSize().padding(pad)
                    .padding(horizontal = 20.dp)
            ) {
                when (page) {
                    "Início" -> HomeScreen(
                        products.size, sales, cash != null, onSell = { navigate("Vender") },
                        onCatalog = { navigate("Catálogo") }, onFinance = { navigate("Financeiro") }
                    )
                    "Catálogo" -> CatalogScreen(
                        products, query, { query = it }, { showNewProduct = true }
                    )
                    "Vender" -> SalesScreen(
                        products, query, { query = it }, cart, { cart = it },
                        discount, { discount = it }, payment, { payment = it },
                        received, { received = it },
                        onFinish = {
                            scope.launch {
                                try {
                                    val gross = cart.sumOf { it.first.salePrice * it.second }
                                    val disc = parseCents(discount)
                                    val total = gross - disc
                                    val paid = if (payment == "DINHEIRO") total else total
                                    if (payment == "DINHEIRO" && parseCents(received) < total) {
                                        toast = "Valor recebido insuficiente."
                                    } else {
                                        val id = repo.finishSale(
                                            cart.map { it.first to it.second.toDouble() },
                                            listOf(payment to paid), null, disc
                                        )
                                        cart = emptyList(); received = ""; discount = "0"
                                        toast = "Venda #$id concluída com sucesso."
                                    }
                                } catch (e: Exception) { toast = e.message ?: "Não foi possível concluir." }
                            }
                        }
                    )
                    "Financeiro" -> FinanceScreen(
                        finance, expenseName, { expenseName = it },
                        expenseAmount, { expenseAmount = it },
                        onExpense = {
                            val amount = parseCents(expenseAmount)
                            if (expenseName.isNotBlank() && amount > 0) scope.launch {
                                dao.insertFinancial(FinancialEntry(
                                    kind = "DESPESA", description = expenseName.trim(),
                                    amount = amount, paid = true
                                ))
                                expenseName = ""; expenseAmount = ""; toast = "Despesa registrada."
                            }
                        }
                    )
                    "Mais" -> MoreScreen(onCash = { navigate("Caixa") }, onCatalog = { navigate("Catálogo") }, onFinance = { navigate("Financeiro") })
                    "Caixa" -> CashScreen(
                        cash != null, cashValue, { cashValue = it },
                        cashDescription, { cashDescription = it },
                        onOpen = { scope.launch {
                            try { repo.openCash(parseCents(cashValue)); cashValue = ""; toast = "Caixa aberto." }
                            catch (e: Exception) { toast = e.message ?: "Erro ao abrir caixa." }
                        } },
                        onMove = { type -> scope.launch {
                            try {
                                repo.cashMovement(type, parseCents(cashValue), cashDescription)
                                cashValue = ""; cashDescription = ""; toast = "Movimentação registrada."
                            } catch (e: Exception) { toast = e.message ?: "Erro na movimentação." }
                        } },
                        onClose = { scope.launch {
                            try {
                                val result = repo.closeCash(parseCents(cashValue))
                                cashValue = ""; toast = "Caixa fechado. Diferença: ${money(result.second)}"
                            } catch (e: Exception) { toast = e.message ?: "Erro ao fechar caixa." }
                        } }
                    )
                }
            }
        }
        if (showNewProduct) {
            NewProductDialog(
                onDismiss = { showNewProduct = false },
                onSave = { name, code, price, cost, stock ->
                    scope.launch {
                        try {
                            dao.insertProduct(Product(
                                name = name, code = code, salePrice = parseCents(price),
                                costPrice = parseCents(cost), stock = stock.toDoubleOrNull() ?: 0.0
                            ))
                            showNewProduct = false
                            toast = "Produto cadastrado."
                        } catch (e: Exception) { toast = "Confira os dados e o código do produto." }
                    }
                }
            )
        }
    }
}

@Composable
private fun HomeScreen(
    productCount: Int, sales: List<Sale>, cashOpen: Boolean,
    onSell: () -> Unit, onCatalog: () -> Unit, onFinance: () -> Unit
) {
    val today = sales.filter { isToday(it.createdAt) }
    val revenue = today.sumOf { it.total }
    val average = if (today.isEmpty()) 0L else revenue / today.size
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
        Spacer(Modifier.height(18.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("BOM DIA 👋", color = Muted, fontSize = 11.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.5.sp)
                Text("Visão geral", color = Ink, fontSize = 28.sp, fontWeight = FontWeight.ExtraBold)
                Text("Aqui está o resumo da sua loja.", color = Muted, fontSize = 13.sp)
            }
            Box(Modifier.size(46.dp).clip(CircleShape).background(Color(0xFFEAE9FF)), contentAlignment = Alignment.Center) {
                Icon(Icons.Rounded.Storefront, null, tint = Purple)
            }
        }
        Spacer(Modifier.height(22.dp))
        Box(
            Modifier.fillMaxWidth().clip(RoundedCornerShape(28.dp))
                .background(Brush.linearGradient(listOf(Color(0xFF4E49E8), Color(0xFF817BFF))))
                .padding(22.dp)
        ) {
            Column {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("FATURAMENTO DE HOJE", color = White.copy(alpha = .78f), fontSize = 11.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.sp, modifier = Modifier.weight(1f))
                    Icon(Icons.Rounded.TrendingUp, null, tint = White.copy(alpha = .9f))
                }
                Spacer(Modifier.height(8.dp))
                Text(money(revenue), color = White, fontSize = 34.sp, fontWeight = FontWeight.ExtraBold)
                Spacer(Modifier.height(20.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(24.dp)) {
                    MetricMini("VENDAS", today.size.toString())
                    MetricMini("TICKET MÉDIO", money(average))
                    MetricMini("CAIXA", if (cashOpen) "Aberto" else "Fechado")
                }
            }
        }
        Spacer(Modifier.height(22.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("Acesso rápido", fontSize = 18.sp, fontWeight = FontWeight.Bold, color = Ink, modifier = Modifier.weight(1f))
            Text("$productCount produtos", color = Muted, fontSize = 12.sp)
        }
        Spacer(Modifier.height(12.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            QuickAction("Nova venda", "Atender cliente", Icons.Rounded.PointOfSale, Color(0xFFECEBFF), Purple, onSell, Modifier.weight(1f))
            QuickAction("Catálogo", "Seus produtos", Icons.Rounded.Inventory2, Color(0xFFE5F8F2), Mint, onCatalog, Modifier.weight(1f))
        }
        Spacer(Modifier.height(12.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            QuickAction("Financeiro", "Entradas e saídas", Icons.Rounded.AccountBalanceWallet, Color(0xFFFFF2E2), Amber, onFinance, Modifier.weight(1f))
            QuickAction("Relatórios", "Acompanhe resultados", Icons.Rounded.QueryStats, Color(0xFFFFEAF0), Rose, onFinance, Modifier.weight(1f))
        }
        Spacer(Modifier.height(22.dp))
        Text("Movimento recente", fontSize = 18.sp, fontWeight = FontWeight.Bold, color = Ink)
        Spacer(Modifier.height(8.dp))
        if (today.isEmpty()) {
            Surface(shape = RoundedCornerShape(20.dp), color = White, modifier = Modifier.fillMaxWidth()) {
                Column(Modifier.padding(20.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(Icons.Rounded.AutoGraph, null, tint = Violet, modifier = Modifier.size(30.dp))
                    Spacer(Modifier.height(8.dp))
                    Text("Seu dia está começando", fontWeight = FontWeight.SemiBold, color = Ink)
                    Text("As vendas realizadas aparecerão aqui.", color = Muted, fontSize = 12.sp)
                }
            }
        } else {
            today.take(4).forEach { sale ->
                Surface(
                    color = White,
                    shape = RoundedCornerShape(17.dp),
                    modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp)
                ) {
                    Row(
                        Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            Modifier.size(38.dp).clip(RoundedCornerShape(12.dp))
                                .background(Color(0xFFECEBFF)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(Icons.Rounded.ReceiptLong, null, tint = Purple, modifier = Modifier.size(19.dp))
                        }
                        Spacer(Modifier.width(11.dp))
                        Column(Modifier.weight(1f)) {
                            Text("Venda #${sale.id}", color = Ink, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                            Text("Concluída • ${java.text.SimpleDateFormat("HH:mm", Locale("pt", "BR")).format(java.util.Date(sale.createdAt))}",
                                color = Muted, fontSize = 11.sp)
                        }
                        Text(money(sale.total), color = Mint, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                    }
                }
            }
        }
    }
}

@Composable private fun MetricMini(label: String, value: String) {
    Column(Modifier.widthIn(max = 112.dp)) {
        Text(label, color = White.copy(alpha = .7f), fontSize = 9.sp, fontWeight = FontWeight.Bold)
        Text(value, color = White, fontSize = 13.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

@Composable private fun QuickAction(
    title: String, subtitle: String, icon: androidx.compose.ui.graphics.vector.ImageVector,
    tintBg: Color, tint: Color, onClick: () -> Unit, modifier: Modifier = Modifier
) {
    Surface(modifier.clickable(onClick = onClick), color = White, shape = RoundedCornerShape(20.dp), shadowElevation = 1.dp) {
        Column(Modifier.padding(15.dp)) {
            Box(Modifier.size(38.dp).clip(RoundedCornerShape(13.dp)).background(tintBg), contentAlignment = Alignment.Center) {
                Icon(icon, null, tint = tint, modifier = Modifier.size(20.dp))
            }
            Spacer(Modifier.height(12.dp))
            Text(title, fontWeight = FontWeight.Bold, color = Ink, fontSize = 14.sp)
            Text(subtitle, color = Muted, fontSize = 10.sp)
        }
    }
}

@Composable
private fun CatalogScreen(products: List<Product>, query: String, onQuery: (String) -> Unit, onAdd: () -> Unit) {
    Column(Modifier.fillMaxSize()) {
        Spacer(Modifier.height(22.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("SEU NEGÓCIO", color = Purple, fontSize = 10.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.5.sp)
                Text("Catálogo", color = Ink, fontSize = 28.sp, fontWeight = FontWeight.ExtraBold)
                Text("${products.size} itens cadastrados", color = Muted, fontSize = 13.sp)
            }
            FilledTonalIconButton(onClick = onAdd, colors = IconButtonDefaults.filledTonalIconButtonColors(containerColor = Purple, contentColor = White), modifier = Modifier.size(50.dp)) {
                Icon(Icons.Rounded.Add, null)
            }
        }
        Spacer(Modifier.height(18.dp))
        OutlinedTextField(query, onQuery, Modifier.fillMaxWidth(), placeholder = { Text("Buscar por nome ou código") }, leadingIcon = { Icon(Icons.Rounded.Search, null, tint = Muted) }, shape = RoundedCornerShape(17.dp), singleLine = true)
        Spacer(Modifier.height(12.dp))
        LazyColumn(verticalArrangement = Arrangement.spacedBy(9.dp), contentPadding = PaddingValues(bottom = 18.dp)) {
            items(products.filter { it.name.contains(query, true) || it.code.contains(query, true) }) { p ->
                Surface(color = White, shape = RoundedCornerShape(19.dp), shadowElevation = 1.dp) {
                    Row(Modifier.fillMaxWidth().padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                        Box(Modifier.size(45.dp).clip(RoundedCornerShape(14.dp)).background(Color(0xFFECEBFF)), contentAlignment = Alignment.Center) {
                            Icon(Icons.Rounded.Inventory2, null, tint = Purple)
                        }
                        Spacer(Modifier.width(12.dp))
                        Column(Modifier.weight(1f)) {
                            Text(p.name, color = Ink, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Text("Cód. ${p.code}  •  Estoque ${p.stock}", color = Muted, fontSize = 11.sp)
                        }
                        Text(money(p.salePrice), color = Purple, fontWeight = FontWeight.ExtraBold, fontSize = 14.sp)
                    }
                }
            }
        }
    }
}

@Composable
private fun SalesScreen(
    products: List<Product>, query: String, onQuery: (String) -> Unit,
    cart: List<Pair<Product, Int>>, onCart: (List<Pair<Product, Int>>) -> Unit,
    discount: String, onDiscount: (String) -> Unit,
    payment: String, onPayment: (String) -> Unit,
    received: String, onReceived: (String) -> Unit, onFinish: () -> Unit
) {
    val gross = cart.sumOf { it.first.salePrice * it.second }
    val disc = parseCents(discount).coerceIn(0, gross)
    val total = gross - disc

    Column(Modifier.fillMaxSize()) {
        Spacer(Modifier.height(10.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("PONTO DE VENDA", color = Purple, fontSize = 10.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.3.sp)
                Text("Nova venda", color = Ink, fontSize = 25.sp, fontWeight = FontWeight.ExtraBold)
            }
            Surface(color = Color(0xFFECEBFF), shape = RoundedCornerShape(13.dp)) {
                Text("${cart.sumOf { it.second }} itens", color = Purple, fontWeight = FontWeight.Bold, fontSize = 12.sp,
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 9.dp))
            }
        }
        Spacer(Modifier.height(10.dp))
        OutlinedTextField(
            query, onQuery, Modifier.fillMaxWidth(),
            placeholder = { Text("Buscar produto ou código") },
            leadingIcon = { Icon(Icons.Rounded.Search, null) },
            shape = RoundedCornerShape(15.dp), singleLine = true
        )
        Spacer(Modifier.height(8.dp))

        // A área de produtos ocupa apenas a parte superior; o carrinho permanece visível.
        LazyColumn(
            Modifier.weight(0.28f).fillMaxWidth(),
            contentPadding = PaddingValues(bottom = 8.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            items(products.filter { it.name.contains(query, true) || it.code.contains(query, true) }.take(30)) { product ->
                Surface(
                    color = White, shape = RoundedCornerShape(14.dp),
                    modifier = Modifier.fillMaxWidth().clickable {
                        onCart(
                            if (cart.any { it.first.id == product.id })
                                cart.map { if (it.first.id == product.id) it.first to it.second + 1 else it }
                            else cart + (product to 1)
                        )
                    }
                ) {
                    Row(Modifier.padding(horizontal = 12.dp, vertical = 9.dp), verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(product.name, color = Ink, fontWeight = FontWeight.SemiBold, fontSize = 13.sp,
                                maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Text("${money(product.salePrice)}  •  estoque ${product.stock}", color = Muted, fontSize = 10.sp)
                        }
                        Icon(Icons.Rounded.AddCircle, null, tint = Purple, modifier = Modifier.size(23.dp))
                    }
                }
            }
        }

        Surface(
            color = White,
            shape = RoundedCornerShape(topStart = 22.dp, topEnd = 22.dp),
            shadowElevation = 5.dp,
            modifier = Modifier.fillMaxWidth().weight(0.72f)
        ) {
            Column(Modifier.fillMaxSize().padding(horizontal = 13.dp, vertical = 10.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Carrinho", color = Ink, fontSize = 15.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                    Text(if (cart.isEmpty()) "Vazio" else "${cart.size} produtos", color = Muted, fontSize = 11.sp)
                    if (cart.isNotEmpty()) {
                        TextButton(onClick = { onCart(emptyList()) }, contentPadding = PaddingValues(horizontal = 7.dp, vertical = 0.dp)) {
                            Text("Limpar", color = Rose, fontSize = 11.sp)
                        }
                    }
                }
                if (cart.isEmpty()) {
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(vertical = 7.dp)) {
                        Icon(Icons.Rounded.ShoppingCart, null, tint = Muted, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(8.dp))
                        Text("Toque em um produto para adicionar.", color = Muted, fontSize = 11.sp)
                    }
                } else {
                    LazyColumn(
                        Modifier.weight(1f, fill = false).heightIn(max = 112.dp),
                        verticalArrangement = Arrangement.spacedBy(2.dp)
                    ) {
                        items(cart) { (product, quantity) ->
                            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                                Text("${quantity}×", color = Purple, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                                Spacer(Modifier.width(7.dp))
                                Text(product.name, color = Ink, fontSize = 11.sp, modifier = Modifier.weight(1f),
                                    maxLines = 1, overflow = TextOverflow.Ellipsis)
                                Text(money(product.salePrice * quantity), color = Ink, fontWeight = FontWeight.SemiBold, fontSize = 11.sp)
                                IconButton(
                                    onClick = {
                                        onCart(if (quantity > 1) cart.map { if (it.first.id == product.id) product to (quantity - 1) else it }
                                        else cart.filterNot { it.first.id == product.id })
                                    },
                                    modifier = Modifier.size(27.dp)
                                ) { Icon(Icons.Rounded.RemoveCircleOutline, null, tint = Rose, modifier = Modifier.size(17.dp)) }
                                IconButton(
                                    onClick = { onCart(cart.map { if (it.first.id == product.id) product to (quantity + 1) else it }) },
                                    modifier = Modifier.size(27.dp)
                                ) { Icon(Icons.Rounded.AddCircleOutline, null, tint = Purple, modifier = Modifier.size(17.dp)) }
                            }
                        }
                    }
                }

                Row(verticalAlignment = Alignment.CenterVertically) {
                    OutlinedTextField(
                        discount, onDiscount, Modifier.weight(1f),
                        label = { Text("Desconto (R$)", fontSize = 11.sp) },
                        singleLine = true, shape = RoundedCornerShape(12.dp)
                    )
                    Spacer(Modifier.width(10.dp))
                    Column(horizontalAlignment = Alignment.End) {
                        Text("TOTAL", color = Muted, fontSize = 9.sp, fontWeight = FontWeight.Bold)
                        Text(money(total), color = Purple, fontSize = 21.sp, fontWeight = FontWeight.ExtraBold, maxLines = 1)
                    }
                }
                Spacer(Modifier.height(4.dp))
                LazyRow(horizontalArrangement = Arrangement.spacedBy(5.dp), contentPadding = PaddingValues(horizontal = 1.dp)) {
                    items(listOf("DINHEIRO", "PIX", "CARTAO_CREDITO", "CARTAO_DEBITO", "FIADO")) { method ->
                        FilterChip(
                            selected = payment == method,
                            onClick = { onPayment(method) },
                            label = {
                                Text(
                                    when (method) {
                                        "CARTAO_CREDITO" -> "Crédito"
                                        "CARTAO_DEBITO" -> "Débito"
                                        else -> method.replaceFirstChar { it.uppercase() }
                                    },
                                    maxLines = 1, softWrap = false, fontSize = 10.sp
                                )
                            },
                            modifier = Modifier.height(36.dp)
                        )
                    }
                }
                if (payment == "DINHEIRO") {
                    OutlinedTextField(
                        received, onReceived, Modifier.fillMaxWidth(),
                        label = { Text("Valor recebido (R$)") },
                        singleLine = true, shape = RoundedCornerShape(12.dp)
                    )
                    if (parseCents(received) >= total && total > 0) {
                        Text("Troco: ${money(parseCents(received) - total)}", color = Mint, fontWeight = FontWeight.Bold, fontSize = 11.sp)
                    }
                }
                Spacer(Modifier.height(6.dp))
                Button(
                    onClick = onFinish,
                    enabled = cart.isNotEmpty() && total > 0 && (payment != "DINHEIRO" || parseCents(received) >= total),
                    modifier = Modifier.fillMaxWidth().height(47.dp),
                    shape = RoundedCornerShape(14.dp)
                ) {
                    Icon(Icons.Rounded.CheckCircle, null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(7.dp))
                    Text("Finalizar venda", fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

@Composable
private fun FinanceScreen(
    entries: List<FinancialEntry>, name: String, onName: (String) -> Unit,
    amount: String, onAmount: (String) -> Unit, onExpense: () -> Unit
) {
    val paidIn = entries.filter { it.paid && it.kind == "RECEITA" }.sumOf { it.amount }
    val paidOut = entries.filter { it.paid && it.kind == "DESPESA" }.sumOf { it.amount }
    Column(Modifier.fillMaxSize()) {
        Spacer(Modifier.height(22.dp))
        Text("CONTROLE DA LOJA", color = Purple, fontSize = 10.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.5.sp)
        Text("Financeiro", color = Ink, fontSize = 28.sp, fontWeight = FontWeight.ExtraBold)
        Spacer(Modifier.height(17.dp))
        Surface(color = White, shape = RoundedCornerShape(22.dp), shadowElevation = 1.dp) {
            Column(Modifier.padding(18.dp)) {
                Text("SALDO REGISTRADO", color = Muted, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                Text(money(paidIn - paidOut), color = Ink, fontSize = 29.sp, fontWeight = FontWeight.ExtraBold)
                Spacer(Modifier.height(15.dp))
                Row {
                    FinanceMetric("Entradas", money(paidIn), Mint, Modifier.weight(1f))
                    FinanceMetric("Saídas", money(paidOut), Rose, Modifier.weight(1f))
                }
            }
        }
        Spacer(Modifier.height(18.dp))
        Text("Registrar despesa", color = Ink, fontSize = 17.sp, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(8.dp))
        OutlinedTextField(name, onName, Modifier.fillMaxWidth(), label = { Text("Descrição") }, shape = RoundedCornerShape(14.dp))
        Spacer(Modifier.height(8.dp))
        OutlinedTextField(amount, onAmount, Modifier.fillMaxWidth(), label = { Text("Valor (R$)") }, shape = RoundedCornerShape(14.dp))
        Spacer(Modifier.height(8.dp))
        Button(onClick = onExpense, modifier = Modifier.fillMaxWidth().height(49.dp), shape = RoundedCornerShape(15.dp)) { Text("Salvar despesa") }
        Spacer(Modifier.height(18.dp))
        Text("Últimos lançamentos", color = Ink, fontSize = 17.sp, fontWeight = FontWeight.Bold)
        LazyColumn {
            items(entries.take(30)) { e ->
                ListItem(
                    headlineContent = { Text(e.description, fontWeight = FontWeight.SemiBold) },
                    supportingContent = { Text(e.kind.replace("_", " "), color = Muted) },
                    trailingContent = { Text(money(e.amount), fontWeight = FontWeight.Bold, color = if (e.kind == "RECEITA") Mint else Rose) },
                    colors = ListItemDefaults.colors(containerColor = White)
                )
                HorizontalDivider(color = Canvas)
            }
        }
    }
}

@Composable private fun FinanceMetric(label: String, value: String, color: Color, modifier: Modifier) {
    Column(modifier) {
        Text(label, color = Muted, fontSize = 11.sp)
        Text(value, color = color, fontSize = 15.sp, fontWeight = FontWeight.Bold)
    }
}

@Composable
private fun CashScreen(
    isOpen: Boolean, value: String, onValue: (String) -> Unit,
    description: String, onDescription: (String) -> Unit,
    onOpen: () -> Unit, onMove: (String) -> Unit, onClose: () -> Unit
) {
    Column(Modifier.fillMaxSize()) {
        Spacer(Modifier.height(22.dp))
        Text("OPERAÇÃO", color = Purple, fontSize = 10.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.5.sp)
        Text("Controle de caixa", color = Ink, fontSize = 27.sp, fontWeight = FontWeight.ExtraBold)
        Spacer(Modifier.height(20.dp))
        Surface(color = White, shape = RoundedCornerShape(24.dp), shadowElevation = 1.dp, modifier = Modifier.fillMaxWidth()) {
            Column(Modifier.padding(20.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                Box(Modifier.size(60.dp).clip(CircleShape).background(if (isOpen) Color(0xFFE5F8F2) else Color(0xFFECEBFF)), contentAlignment = Alignment.Center) {
                    Icon(Icons.Rounded.PointOfSale, null, tint = if (isOpen) Mint else Purple, modifier = Modifier.size(28.dp))
                }
                Spacer(Modifier.height(10.dp))
                Text(if (isOpen) "Caixa aberto" else "Caixa fechado", color = Ink, fontSize = 20.sp, fontWeight = FontWeight.Bold)
                Text(if (isOpen) "Registre entradas, retiradas ou encerre o turno." else "Abra o caixa informando o fundo inicial.", color = Muted, fontSize = 12.sp)
                Spacer(Modifier.height(18.dp))
                OutlinedTextField(value, onValue, Modifier.fillMaxWidth(), label = { Text(if (isOpen) "Valor da movimentação / contado" else "Fundo de troco (R$)") }, shape = RoundedCornerShape(14.dp))
                if (isOpen) {
                    Spacer(Modifier.height(8.dp))
                    OutlinedTextField(description, onDescription, Modifier.fillMaxWidth(), label = { Text("Descrição") }, shape = RoundedCornerShape(14.dp))
                    Spacer(Modifier.height(12.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedButton(onClick = { onMove("SANGRIA") }, modifier = Modifier.weight(1f)) { Text("Sangria") }
                        OutlinedButton(onClick = { onMove("SUPRIMENTO") }, modifier = Modifier.weight(1f)) { Text("Suprimento") }
                    }
                    Spacer(Modifier.height(8.dp))
                    Button(onClick = onClose, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(14.dp)) { Text("Fechar caixa") }
                } else {
                    Button(onClick = onOpen, modifier = Modifier.fillMaxWidth().height(50.dp), shape = RoundedCornerShape(14.dp)) { Text("Abrir caixa") }
                }
            }
        }
    }
}

@Composable
private fun MoreScreen(onCash: () -> Unit, onCatalog: () -> Unit, onFinance: () -> Unit) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(top = 22.dp, bottom = 28.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        item {
            Column {
                Text("PERSONALIZE", color = Purple, fontSize = 10.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.5.sp)
                Text("Mais opções", color = Ink, fontSize = 28.sp, fontWeight = FontWeight.ExtraBold)
                Spacer(Modifier.height(10.dp))
                Surface(color = White, shape = RoundedCornerShape(22.dp), modifier = Modifier.fillMaxWidth()) {
                    Row(Modifier.padding(18.dp), verticalAlignment = Alignment.CenterVertically) {
                        Box(Modifier.size(48.dp).clip(CircleShape).background(Color(0xFFECEBFF)), contentAlignment = Alignment.Center) {
                            Icon(Icons.Rounded.Person, null, tint = Purple)
                        }
                        Spacer(Modifier.width(12.dp))
                        Column {
                            Text("Perfil do proprietário", fontWeight = FontWeight.Bold, color = Ink)
                            Text("Administrador da loja", color = Muted, fontSize = 12.sp)
                        }
                    }
                }
            }
        }
        item { MoreRow("Caixa", "Abertura, sangria e fechamento", Icons.Rounded.PointOfSale, onCash) }
        item { MoreRow("Catálogo", "Produtos e serviços", Icons.Rounded.Inventory2, onCatalog) }
        item { MoreRow("Financeiro", "Entradas, saídas e lançamentos", Icons.Rounded.AccountBalanceWallet, onFinance) }
        item { MoreRow("Clientes", "Cadastro e histórico", Icons.Rounded.People) {} }
        item { MoreRow("Relatórios", "Indicadores e desempenho", Icons.Rounded.QueryStats) {} }
        item { MoreRow("Aparelho e impressão", "Impressora e preferências", Icons.Rounded.Print) {} }
        item { MoreRow("Aparência", "Tema e personalização", Icons.Rounded.Palette) {} }
    }
}

@Composable private fun MoreRow(title: String, subtitle: String, icon: androidx.compose.ui.graphics.vector.ImageVector, onClick: () -> Unit) {
    Surface(color = White, shape = RoundedCornerShape(17.dp), modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp).clickable(onClick = onClick)) {
        Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(icon, null, tint = Purple, modifier = Modifier.size(22.dp))
            Spacer(Modifier.width(13.dp))
            Column(Modifier.weight(1f)) {
                Text(title, color = Ink, fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
                Text(subtitle, color = Muted, fontSize = 10.sp)
            }
            Icon(Icons.Rounded.ChevronRight, null, tint = Muted)
        }
    }
}

@Composable
private fun NewProductDialog(
    onDismiss: () -> Unit,
    onSave: (String, String, String, String, String) -> Unit
) {
    var name by remember { mutableStateOf("") }
    var code by remember { mutableStateOf("") }
    var price by remember { mutableStateOf("") }
    var cost by remember { mutableStateOf("0") }
    var stock by remember { mutableStateOf("0") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Novo produto", fontWeight = FontWeight.Bold) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(7.dp)) {
                OutlinedTextField(name, { name = it }, label = { Text("Nome") }, singleLine = true)
                OutlinedTextField(code, { code = it }, label = { Text("Código") }, singleLine = true)
                OutlinedTextField(price, { price = it }, label = { Text("Preço de venda") }, singleLine = true)
                OutlinedTextField(cost, { cost = it }, label = { Text("Preço de custo") }, singleLine = true)
                OutlinedTextField(stock, { stock = it }, label = { Text("Estoque inicial") }, singleLine = true)
            }
        },
        confirmButton = { Button(onClick = { if (name.isNotBlank() && code.isNotBlank() && parseCents(price) > 0) onSave(name.trim(), code.trim(), price, cost, stock) }) { Text("Salvar produto") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancelar") } }
    )
}

private fun parseCents(value: String): Long {
    val raw = value.trim().replace("R$", "", ignoreCase = true).replace(" ", "")
    if (raw.isBlank()) return 0L
    val normalized = if (raw.contains(",")) raw.replace(".", "").replace(",", ".") else raw
    return try { (normalized.toBigDecimal() * 100.toBigDecimal()).setScale(0, java.math.RoundingMode.HALF_UP).longValueExact() } catch (_: Exception) { 0L }
}
private fun isToday(time: Long): Boolean {
    val a = java.util.Calendar.getInstance()
    val b = java.util.Calendar.getInstance().apply { timeInMillis = time }
    return a.get(java.util.Calendar.YEAR) == b.get(java.util.Calendar.YEAR) &&
        a.get(java.util.Calendar.DAY_OF_YEAR) == b.get(java.util.Calendar.DAY_OF_YEAR)
}
