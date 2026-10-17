package com.example.detectormovimiento

import android.app.Application
import android.os.Build
import java.io.File
import java.io.PrintWriter
import java.io.StringWriter
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Si la app se cierra por un error, guarda el detalle en un archivo para mostrarlo
 * la próxima vez que se abra (así el equipo puede reportar qué pasó).
 */
class EhriaApp : Application() {

    override fun onCreate() {
        super.onCreate()
        val anterior = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { hilo, error ->
            try {
                val traza = StringWriter().also { error.printStackTrace(PrintWriter(it)) }
                val fecha = SimpleDateFormat("dd/MM/yyyy HH:mm:ss", Locale.getDefault()).format(Date())
                archivoError(this).writeText(
                    "Fecha: $fecha\nTeléfono: ${Build.MANUFACTURER} ${Build.MODEL} (Android ${Build.VERSION.RELEASE})\n\n$traza"
                )
            } catch (_: Exception) {
                // Si no se puede guardar, igual se deja que el sistema cierre la app.
            }
            anterior?.uncaughtException(hilo, error)
        }
    }

    companion object {
        fun archivoError(app: Application) = File(app.filesDir, "ultimo_error.txt")
    }
}
