package com.example.detectormovimiento

import com.example.detectormovimiento.DetectorSismico.Nivel
import java.io.File

/**
 * Guarda el historial de sismos y de mediciones en dos archivos de texto dentro del
 * teléfono (una línea por registro, campos separados por tabulador).
 *
 * Es tolerante a errores: si una línea está dañada se ignora, nunca cierra la app.
 */
class HistorialRepositorio(private val carpeta: File) {

    data class RegistroSismo(
        val id: Long,
        val fechaMs: Long,
        val nivel: Nivel,
        val pgaPico: Float,
        val duracionS: Int,
        /** Pico sin filtrar, para estimar magnitud (Richter) e intensidad (Mercalli). */
        val pgaPicoBruto: Float = pgaPico,
    )

    enum class TipoMedicion { RESPIRACION, PULSO }

    data class RegistroMedicion(
        val id: Long,
        val fechaMs: Long,
        val tipo: TipoMedicion,
        val rpm: Int?,
        val bpm: Int?,
        val resultado: String,
        val intentosPulso: Int,
    )

    private val archivoSismos get() = File(carpeta, "historial_sismos.tsv")
    private val archivoMediciones get() = File(carpeta, "historial_mediciones.tsv")

    /** Más recientes primero. */
    fun sismos(): List<RegistroSismo> =
        leerLineas(archivoSismos).mapNotNull(::parsearSismo).sortedByDescending { it.fechaMs }

    /** Más recientes primero. */
    fun mediciones(): List<RegistroMedicion> =
        leerLineas(archivoMediciones).mapNotNull(::parsearMedicion).sortedByDescending { it.fechaMs }

    /** Agrega o reemplaza (mismo id) un sismo. */
    fun guardarSismo(registro: RegistroSismo) {
        val lista = sismos().filter { it.id != registro.id } + registro
        escribir(archivoSismos, lista.sortedByDescending { it.fechaMs }.take(MAXIMO).map(::formatearSismo))
    }

    /** Agrega o reemplaza (mismo id) una medición. */
    fun guardarMedicion(registro: RegistroMedicion) {
        val lista = mediciones().filter { it.id != registro.id } + registro
        escribir(archivoMediciones, lista.sortedByDescending { it.fechaMs }.take(MAXIMO).map(::formatearMedicion))
    }

    fun borrarTodo() {
        archivoSismos.delete()
        archivoMediciones.delete()
    }

    private fun formatearSismo(r: RegistroSismo) =
        listOf(r.id, r.fechaMs, r.nivel.name, r.pgaPico, r.duracionS, r.pgaPicoBruto).joinToString("\t")

    private fun parsearSismo(linea: String): RegistroSismo? {
        val c = linea.split('\t')
        if (c.size < 5) return null
        val pgaPico = c[3].toFloatOrNull() ?: return null
        return RegistroSismo(
            id = c[0].toLongOrNull() ?: return null,
            fechaMs = c[1].toLongOrNull() ?: return null,
            nivel = Nivel.entries.firstOrNull { it.name == c[2] } ?: return null,
            pgaPico = pgaPico,
            duracionS = c[4].toIntOrNull() ?: return null,
            // Registros anteriores no traen el pico sin filtrar: se usa el filtrado.
            pgaPicoBruto = c.getOrNull(5)?.toFloatOrNull() ?: pgaPico,
        )
    }

    private fun formatearMedicion(r: RegistroMedicion) =
        listOf(r.id, r.fechaMs, r.rpm ?: "", r.bpm ?: "", limpiar(r.resultado), r.intentosPulso, r.tipo.name)
            .joinToString("\t")

    private fun parsearMedicion(linea: String): RegistroMedicion? {
        val c = linea.split('\t')
        if (c.size < 6) return null
        val rpm = c[2].toIntOrNull()
        val bpm = c[3].toIntOrNull()
        // Registros viejos (medición combinada) no traen tipo: se deduce del valor guardado.
        val tipo = TipoMedicion.entries.firstOrNull { it.name == c.getOrNull(6) }
            ?: if (bpm != null && rpm == null) TipoMedicion.PULSO else TipoMedicion.RESPIRACION
        return RegistroMedicion(
            id = c[0].toLongOrNull() ?: return null,
            fechaMs = c[1].toLongOrNull() ?: return null,
            tipo = tipo,
            rpm = rpm,
            bpm = bpm,
            resultado = c[4],
            intentosPulso = c[5].toIntOrNull() ?: 0,
        )
    }

    private fun limpiar(texto: String) = texto.replace('\t', ' ').replace('\n', ' ')

    private fun leerLineas(archivo: File): List<String> = try {
        if (archivo.exists()) archivo.readLines().filter { it.isNotBlank() } else emptyList()
    } catch (_: Exception) {
        emptyList()
    }

    private fun escribir(archivo: File, lineas: List<String>) {
        try {
            carpeta.mkdirs()
            // Se escribe a un archivo temporal y luego se reemplaza, para no dejar el
            // historial a medias si la app se cierra mientras guarda.
            val temporal = File(carpeta, archivo.name + ".tmp")
            temporal.writeText(lineas.joinToString("\n", postfix = if (lineas.isEmpty()) "" else "\n"))
            if (!temporal.renameTo(archivo)) {
                archivo.delete()
                temporal.renameTo(archivo)
            }
        } catch (_: Exception) {
            // Sin espacio o sin permisos: se pierde el registro, pero la app sigue funcionando.
        }
    }

    companion object {
        const val MAXIMO = 200
    }
}
