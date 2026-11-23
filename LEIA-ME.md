# Meu PDV — base Android offline

Projeto Android nativo em Kotlin + Jetpack Compose + Room/SQLite.

## Abrir
1. Extraia o ZIP.
2. Abra a pasta `PDVOffline` no Android Studio (Giraffe ou mais recente).
3. Aguarde a sincronização Gradle.
4. Execute em emulador ou aparelho Android 8.0+.

Requer JDK 17 e Android SDK 35.

## Implementado nesta entrega
- Persistência local Room/SQLite.
- Cadastro de produtos com código único, preço, custo e estoque inicial.
- Pesquisa de produtos por nome/código/código de barras.
- Arquivamento de produtos.
- Cadastro básico de clientes.
- Carrinho de vendas e baixa de estoque.
- Pagamento manual em dinheiro, PIX, cartões e fiado.
- Validação de soma dos pagamentos e cálculo de troco para dinheiro.
- Registro financeiro de recebimentos e contas a receber do fiado.
- Abertura de caixa, suprimento, sangria e fechamento com valor contado.
- Dashboard com total e quantidade de vendas.
- Tema claro/escuro.
- Sem permissão INTERNET e sem dependência de servidor.

## Limitações que precisam ser concluídas antes de uso comercial
Esta é uma primeira base funcional, não uma implementação integral de todos os requisitos extensos do briefing. Ainda não estão implementados: assistente de primeiro acesso/PIN seguro, edição detalhada de produto/cliente, seleção de cliente no checkout, pagamento dividido na interface (a camada de dados valida múltiplas formas), recebimento e baixa de fiado, contas a pagar completas, cálculo detalhado de fechamento e divergência, relatórios de lucro por período, importação CSV/XLSX, backup/exportação, leitor de câmera, impressão Bluetooth, operadores/permissões e testes instrumentados em aparelhos.

O fechamento atual registra o valor contado e encerra a sessão; não deve ser usado como fechamento fiscal/contábil sem concluir a apuração detalhada. A implementação não foi compilada nem executada em dispositivo neste ambiente; faça a sincronização e os testes no Android Studio antes de distribuir.

## Integridade
A finalização da venda ocorre em transação Room: valida estoque, grava venda/itens/pagamentos, baixa estoque e registra lançamentos financeiros. Valores monetários são armazenados em centavos (Long), evitando erros comuns de ponto flutuante.
