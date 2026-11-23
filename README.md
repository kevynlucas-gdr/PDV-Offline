# Meu PDV — projeto Android offline

Projeto Android nativo em Kotlin + Jetpack Compose + Room/SQLite.

## Estrutura
Mantidos os caminhos principais do projeto original:
- `app/src/main/java/br/com/pdvoffline/MainActivity.kt`
- `app/src/main/java/br/com/pdvoffline/PdvApplication.kt`
- `app/src/main/java/br/com/pdvoffline/data/AppDatabase.kt`
- `app/src/main/java/br/com/pdvoffline/data/PdvRepository.kt`

## Recursos nesta reconstrução
- Cadastro local de produtos com código único e estoque.
- Catálogo com pesquisa.
- Carrinho, desconto, pagamentos em dinheiro, PIX, cartões e fiado.
- Validação de estoque e pagamentos dentro de transação Room.
- Baixa de estoque ao concluir venda.
- Lançamentos de receita e contas a receber do fiado.
- Abertura, suprimento, sangria e fechamento de caixa com apuração do valor esperado e diferença.
- Cadastro e dados armazenados localmente no SQLite.
- Sem permissão de internet.

## Abrir e compilar
Abra a pasta raiz `PDVOffline` no Android Studio, com JDK 17 e Android SDK 35. Aguarde a sincronização Gradle e execute `:app:assembleDebug`.

O workflow `.github/workflows/android.yml` compila o APK de depuração no GitHub Actions.

## Importante
Esta é uma reconstrução dos arquivos-fonte nos mesmos caminhos do repositório, não uma cópia binária do ZIP original. Não inclui APK pronto nem Gradle Wrapper. O código não foi compilado neste ambiente; execute a compilação no Android Studio/GitHub Actions antes de usar em operação real. O fechamento de caixa é um controle operacional, não substitui sistema fiscal/contábil.
