package br.com.pdvoffline

import android.app.Application
import br.com.pdvoffline.data.AppDatabase

class PdvApplication : Application() {
    val database: AppDatabase by lazy { AppDatabase.get(this) }
}
