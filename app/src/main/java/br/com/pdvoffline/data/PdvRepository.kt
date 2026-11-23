package br.com.pdvoffline.data

import androidx.room.withTransaction

class PdvRepository(private val db: AppDatabase) {
    private val dao = db.dao()

    suspend fun finishSale(
        cart: List<Pair<Product, Double>>,
        payments: List<Pair<String, Long>>,
        customerId: Long?,
        discount: Long
    ): Long = db.withTransaction {
        require(cart.isNotEmpty()) { "Adicione pelo menos um item." }
        require(cart.all { it.second > 0.0 }) { "Quantidade inválida." }
        val gross = cart.sumOf { (p, q) -> (p.salePrice * q).toLong() }
        require(discount >= 0 && discount <= gross) { "Desconto inválido." }
        val total = gross - discount
        require(total > 0) { "O total da venda deve ser maior que zero." }
        require(payments.isNotEmpty() && payments.all { it.second > 0 }) { "Informe os pagamentos." }
        require(payments.sumOf { it.second } == total) { "A soma dos pagamentos deve ser igual ao total." }

        cart.forEach { (p, q) ->
            val current = dao.product(p.id) ?: error("Produto ${p.name} não encontrado.")
            require(current.active) { "Produto ${p.name} está inativo." }
            if (!current.isService) require(current.stock >= q) { "Estoque insuficiente: ${p.name}." }
        }

        val saleId = dao.insertSale(Sale(customerId = customerId, total = total, discount = discount))
        dao.insertItems(cart.map { (p, q) ->
            SaleItem(
                saleId = saleId, productId = p.id, productName = p.name, quantity = q,
                unitPrice = p.salePrice, unitCost = p.costPrice,
                lineTotal = (p.salePrice * q).toLong()
            )
        })
        dao.insertPayments(payments.map { Payment(saleId = saleId, method = it.first, amount = it.second) })
        cart.forEach { (p, q) -> if (!p.isService) dao.changeStock(p.id, -q) }

        val cash = dao.openCash()
        payments.filter { it.first == "DINHEIRO" }.forEach {
            if (cash != null) dao.insertCashMovement(
                CashMovement(
                    sessionId = cash.id,
                    type = "VENDA_DINHEIRO", amount = it.second, description = "Venda #$saleId")
            )
        }
        payments.filter { it.first != "FIADO" }.forEach {
            dao.insertFinancial(FinancialEntry(kind = "RECEITA", description = "Venda #$saleId - ${it.first}", amount = it.second, paid = true))
        }
        val debt = payments.filter { it.first == "FIADO" }.sumOf { it.second }
        if (debt > 0) {
            dao.insertFinancial(FinancialEntry(kind = "A_RECEBER", description = "Venda fiada #$saleId", amount = debt, customerId = customerId))
        }
        saleId
    }

    suspend fun openCash(floatValue: Long) = db.withTransaction {
        require(dao.openCash() == null) { "Já existe um caixa aberto." }
        require(floatValue >= 0) { "O fundo de troco não pode ser negativo." }
        dao.insertCashSession(CashSession(openingFloat = floatValue))
    }

    suspend fun cashMovement(type: String, amount: Long, description: String) = db.withTransaction {
        require(type == "SUPRIMENTO" || type == "SANGRIA") { "Tipo de movimentação inválido." }
        require(amount > 0) { "Informe um valor maior que zero." }
        require(description.isNotBlank()) { "Informe a descrição." }
        val cash = dao.openCash() ?: error("Abra o caixa primeiro.")
        dao.insertCashMovement(CashMovement(sessionId = cash.id, type = type, amount = amount, description = description.trim()))
    }

    suspend fun closeCash(counted: Long): Pair<Long, Long> = db.withTransaction {
        require(counted >= 0) { "O valor contado não pode ser negativo." }
        val cash = dao.openCash() ?: error("Não há caixa aberto.")
        val movements = dao.cashMovements(cash.id)
        val expected = cash.openingFloat +
            movements.filter { it.type == "VENDA_DINHEIRO" || it.type == "SUPRIMENTO" }.sumOf { it.amount } -
            movements.filter { it.type == "SANGRIA" }.sumOf { it.amount }
        val difference = counted - expected
        dao.updateCashSession(cash.copy(
            closedAt = System.currentTimeMillis(),
            closingCount = counted,
            expectedCash = expected,
            difference = difference,
            isOpen = false
        ))
        expected to difference
    }
}
