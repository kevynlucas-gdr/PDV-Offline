package br.com.pdvoffline

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import br.com.pdvoffline.data.*
import kotlinx.coroutines.launch
import java.math.BigDecimal
import java.math.RoundingMode
import java.text.NumberFormat
import java.util.Locale

private val brl = NumberFormat.getCurrencyInstance(Locale("pt", "BR"))
private fun money(value: Long) = brl.format(value / 100.0)
private fun cents(value: String): Long = try {
    BigDecimal(value.trim().replace(".", "").replace(",", "."))
        .movePointRight(2).setScale(0, RoundingMode.HALF_UP).longValueExact()
} catch (_: Exception) {
    try { BigDecimal(value.trim().replace(",", ".")).movePointRight(2).setScale(0, RoundingMode.HALF_UP).longValueExact() }
    catch (_: Exception) { 0L }
}

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val db = (application as PdvApplication).database
        setContent { PdvApp(db) }
    }
}

@Composable
fun PdvApp(db: AppDatabase) {
    val dao = db.dao()
    val repo = remember { PdvRepository(db) }
    val scope = rememberCoroutineScope()
    val products by dao.observeProducts().collectAsStateWithLifecycle(emptyList())
    val sales by dao.observeSales().collectAsStateWithLifecycle(emptyList())
    val cash by dao.observeOpenCash().collectAsStateWithLifecycle(null)
    val finance by dao.observeFinancial().collectAsStateWithLifecycle(emptyList())
    var tab by remember { mutableStateOf("Início") }
    var notice by remember { mutableStateOf("") }
    var showProduct by remember { mutableStateOf(false) }
    var name by remember { mutableStateOf("") }
    var code by remember { mutableStateOf("") }
    var price by remember { mutableStateOf("") }
    var cost by remember { mutableStateOf("0") }
    var stock by remember { mutableStateOf("0") }
    var search by remember { mutableStateOf("") }
    var cart by remember { mutableStateOf(listOf<Pair<Product, Int>>()) }
    var discount by remember { mutableStateOf("0") }
    var method by remember { mutableStateOf("DINHEIRO") }
    var received by remember { mutableStateOf("") }
    var cashInput by remember { mutableStateOf("") }
    var movement by remember { mutableStateOf("") }
    var movementDesc by remember { mutableStateOf("") }
    var expenseDesc by remember { mutableStateOf("") }
    var expenseValue by remember { mutableStateOf("") }

    MaterialTheme(colorScheme = lightColorScheme(primary = androidx.compose.ui.graphics.Color(0xFF5146E5))) {
        Scaffold(
            bottomBar = {
                NavigationBar {
                    listOf("Início", "Catálogo", "Vender", "Financeiro", "Caixa").forEach { item ->
                        NavigationBarItem(
                            selected = tab == item,
                            onClick = { tab = item },
                            icon = {},
                            label = { Text(item) }
                        )
                    }
                }
            }
        ) { padding ->
            Column(Modifier.fillMaxSize().padding(padding).padding(16.dp)) {
                Text("Meu PDV", style = MaterialTheme.typography.headlineMedium)
                Spacer(Modifier.height(12.dp))
                when (tab) {
                    "Início" -> {
                        Text("Resumo", style = MaterialTheme.typography.titleLarge)
                        Text("Produtos ativos: ${products.size}")
                        Text("Vendas registradas: ${sales.size}")
                        Text("Total vendido: ${money(sales.sumOf { it.total })}")
                        Text(if (cash == null) "Caixa fechado" else "Caixa aberto")
                    }
                    "Catálogo" -> {
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            OutlinedTextField(search, { search = it }, Modifier.weight(1f), label = { Text("Buscar produto") })
                            Button(onClick = { showProduct = true }) { Text("+") }
                        }
                        LazyColumn {
                            items(products.filter { it.name.contains(search, true) || it.code.contains(search, true) }) { p ->
                                ListItem(
                                    headlineContent = { Text(p.name) },
                                    supportingContent = { Text("Código ${p.code} • Estoque ${p.stock}") },
                                    trailingContent = { Text(money(p.salePrice)) }
                                )
                                HorizontalDivider()
                            }
                        }
                    }
                    "Vender" -> {
                        OutlinedTextField(search, { search = it }, Modifier.fillMaxWidth(), label = { Text("Buscar produto") })
                        LazyColumn(Modifier.weight(1f)) {
                            items(products.filter { it.name.contains(search, true) || it.code.contains(search, true) }) { p ->
                                ListItem(
                                    headlineContent = { Text(p.name) },
                                    supportingContent = { Text("${money(p.salePrice)} • estoque ${p.stock}") },
                                    trailingContent = { Button(onClick = {
                                        cart = if (cart.any { it.first.id == p.id }) cart.map { if (it.first.id == p.id) it.first to (it.second + 1) else it }
                                        else cart + (p to 1)
                                    }) { Text("Adicionar") } }
                                )
                            }
                        }
                        Text("Carrinho", style = MaterialTheme.typography.titleMedium)
                        cart.forEach { (p, q) ->
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                Text("${p.name} x$q")
                                Text(money(p.salePrice * q))
                                TextButton(onClick = { cart = cart.filterNot { it.first.id == p.id } }) { Text("Remover") }
                            }
                        }
                        val gross = cart.sumOf { it.first.salePrice * it.second }
                        val disc = cents(discount).coerceIn(0, gross)
                        val total = gross - disc
                        OutlinedTextField(discount, { discount = it }, Modifier.fillMaxWidth(), label = { Text("Desconto (R$)") })
                        Text("Total: ${money(total)}", style = MaterialTheme.typography.titleLarge)
                        var expanded by remember { mutableStateOf(false) }
                        ExposedDropdownMenuBox(expanded, { expanded = it }) {
                            OutlinedTextField(method, {}, Modifier.menuAnchor().fillMaxWidth(), readOnly = true, label = { Text("Pagamento") }, trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded) })
                            ExposedDropdownMenu(expanded, { expanded = false }) {
                                listOf("DINHEIRO", "PIX", "CARTAO_CREDITO", "CARTAO_DEBITO", "FIADO").forEach { option ->
                                    DropdownMenuItem(text = { Text(option.replace("_", " ")) }, onClick = { method = option; expanded = false })
                                }
                            }
                        }
                        if (method == "DINHEIRO") OutlinedTextField(received, { received = it }, Modifier.fillMaxWidth(), label = { Text("Valor recebido (R$)") })
                        Button(
                            onClick = {
                                scope.launch {
                                    try {
                                        val discountCents = cents(discount)
                                        val saleTotal = gross - discountCents
                                        require(discountCents in 0..gross) { "Desconto inválido." }
                                        val paid = if (method == "DINHEIRO") saleTotal else saleTotal
                                        if (method == "DINHEIRO") require(cents(received) >= saleTotal) { "Valor recebido insuficiente." }
                                        val id = repo.finishSale(cart.map { it.first to it.second.toDouble() }, listOf(method to paid), null, discountCents)
                                        cart = emptyList(); received = ""; discount = "0"
                                        notice = "Venda #$id finalizada."
                                    } catch (e: Exception) { notice = e.message ?: "Falha ao finalizar venda." }
                                }
                            },
                            enabled = cart.isNotEmpty() && total > 0 && (method != "DINHEIRO" || cents(received) >= total),
                            modifier = Modifier.fillMaxWidth()
                        ) { Text("Finalizar venda") }
                    }
                    "Financeiro" -> {
                        Text("Saldo de receitas e despesas", style = MaterialTheme.typography.titleMedium)
                        val balance = finance.filter { it.paid }.sumOf { if (it.kind == "RECEITA") it.amount else -it.amount }
                        Text(money(balance), style = MaterialTheme.typography.headlineMedium)
                        OutlinedTextField(expenseDesc, { expenseDesc = it }, Modifier.fillMaxWidth(), label = { Text("Descrição da despesa") })
                        OutlinedTextField(expenseValue, { expenseValue = it }, Modifier.fillMaxWidth(), label = { Text("Valor (R$)") })
                        Button(onClick = {
                            if (expenseDesc.isNotBlank() && cents(expenseValue) > 0) scope.launch {
                                dao.insertFinancial(FinancialEntry(kind = "DESPESA", description = expenseDesc.trim(), amount = cents(expenseValue), paid = true))
                                expenseDesc = ""; expenseValue = ""; notice = "Despesa registrada."
                            }
                        }) { Text("Registrar despesa") }
                        LazyColumn { items(finance) { entry ->
                            ListItem(headlineContent = { Text(entry.description) }, supportingContent = { Text(entry.kind) }, trailingContent = { Text(money(entry.amount)) })
                        } }
                    }
                    "Caixa" -> {
                        if (cash == null) {
                            Text("Caixa fechado")
                            OutlinedTextField(cashInput, { cashInput = it }, Modifier.fillMaxWidth(), label = { Text("Fundo de troco (R$)") })
                            Button(onClick = { scope.launch {
                                try { repo.openCash(cents(cashInput)); cashInput = ""; notice = "Caixa aberto." }
                                catch (e: Exception) { notice = e.message ?: "Erro ao abrir caixa." }
                            } }) { Text("Abrir caixa") }
                        } else {
                            Text("Caixa aberto desde ${java.text.SimpleDateFormat("dd/MM HH:mm", Locale("pt", "BR")).format(cash.openedAt)}")
                            OutlinedTextField(movement, { movement = it }, Modifier.fillMaxWidth(), label = { Text("Valor (R$)") })
                            OutlinedTextField(movementDesc, { movementDesc = it }, Modifier.fillMaxWidth(), label = { Text("Descrição") })
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                Button(onClick = { scope.launch {
                                    try { repo.cashMovement("SUPRIMENTO", cents(movement), movementDesc); notice = "Suprimento registrado."; movement = ""; movementDesc = "" }
                                    catch (e: Exception) { notice = e.message ?: "Erro." }
                                } }) { Text("Suprimento") }
                                Button(onClick = { scope.launch {
                                    try { repo.cashMovement("SANGRIA", cents(movement), movementDesc); notice = "Sangria registrada."; movement = ""; movementDesc = "" }
                                    catch (e: Exception) { notice = e.message ?: "Erro." }
                                } }) { Text("Sangria") }
                            }
                            Button(onClick = { scope.launch {
                                try {
                                    val (expected, diff) = repo.closeCash(cents(movement))
                                    notice = "Caixa fechado. Esperado: ${money(expected)}. Diferença: ${money(diff)}."
                                    movement = ""
                                } catch (e: Exception) { notice = e.message ?: "Erro ao fechar caixa." }
                            } }, modifier = Modifier.fillMaxWidth()) { Text("Fechar caixa (digite o valor contado acima)") }
                        }
                    }
                }
            }
        }
        if (showProduct) {
            AlertDialog(
                onDismissRequest = { showProduct = false },
                title = { Text("Novo produto") },
                text = {
                    Column {
                        OutlinedTextField(name, { name = it }, label = { Text("Nome") })
                        OutlinedTextField(code, { code = it }, label = { Text("Código único") })
                        OutlinedTextField(price, { price = it }, label = { Text("Preço (R$)") })
                        OutlinedTextField(cost, { cost = it }, label = { Text("Custo (R$)") })
                        OutlinedTextField(stock, { stock = it }, label = { Text("Estoque") })
                    }
                },
                confirmButton = {
                    TextButton(onClick = {
                        if (name.isNotBlank() && code.isNotBlank() && cents(price) > 0 && (stock.toDoubleOrNull() ?: -1.0) >= 0) {
                            scope.launch {
                                try {
                                    dao.insertProduct(Product(name = name.trim(), code = code.trim(), salePrice = cents(price), costPrice = cents(cost), stock = stock.toDoubleOrNull() ?: 0.0))
                                    notice = "Produto cadastrado."
                                } catch (_: Exception) { notice = "Código já cadastrado ou dados inválidos." }
                            }
                            name = ""; code = ""; price = ""; cost = "0"; stock = "0"; showProduct = false
                        } else notice = "Preencha nome, código, preço e estoque corretamente."
                    }) { Text("Salvar") }
                },
                dismissButton = { TextButton(onClick = { showProduct = false }) { Text("Cancelar") } }
            )
        }
        if (notice.isNotBlank()) AlertDialog(
            onDismissRequest = { notice = "" },
            title = { Text("Meu PDV") },
            text = { Text(notice) },
            confirmButton = { TextButton(onClick = { notice = "" }) { Text("OK") } }
        )
    }
}
