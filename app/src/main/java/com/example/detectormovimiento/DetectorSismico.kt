package com.example.detectormovimiento

import kotlin.math.max

/**
 * Detector sísmico de una sola estación.
 *
 * - Filtra la magnitud de la aceleración (pasa-bajas, alpha = 0.15).
 * - Aprende el ruido de fondo del teléfono (calibración inicial + seguimiento lento),
 *   así un teléfono viejo y ruidoso no da falsas alarmas.
 * - Un sismo se confirma solo si el movimiento se sostiene [duracionVerificacionMs],
 *   tolerando pausas cortas (las ondas del sismo no son constantes).
 * - La alerta se mantiene [retencionAlertaMs] después de que el movimiento termina.
 *
 * No depende de Android, así que se prueba con pruebas unitarias normales.
 */
class DetectorSismico(
    private val umbralMinimo: Float = 0.05f,          // m/s²
    private val factorRuido: Float = 4f,              // umbral = factor × ruido de fondo
    private val calibracionMs: Long = 3_000L,
    private val duracionVerificacionMs: Long = 10_000L,
    private val toleranciaPausaMs: Long = 1_500L,
    private val retencionAlertaMs: Long = 30_000L,
    private val constanteRuidoMs: Float = 30_000f,
) {
    enum class Fase { CALIBRANDO, NORMAL, EVALUANDO, ALERTA }
    enum class Nivel { NINGUNO, LEVE, MODERADO, FUERTE }

    data class Estado(
        val fase: Fase,
        val nivel: Nivel,
        val pga: Float,
        val pgaPico: Float,
        /** Pico de la señal sin filtrar: es el que usan las escalas de Mercalli y Richter. */
        val pgaPicoBruto: Float,
        val umbral: Float,
        val segundosEvaluados: Int,
        /** Desde que empezó el movimiento hasta la última vez que superó el umbral. */
        val duracionMovimientoS: Int,
        /** true solo en la muestra en que la alerta empieza o sube de nivel. */
        val alertaNueva: Boolean,
    )

    private val alpha = 0.15f

    var pgaFiltrado = 0f
        private set

    private var fase = Fase.CALIBRANDO
    private var nivel = Nivel.NINGUNO
    private var inicioMs = -1L
    private var ultimoMs = -1L
    private var inicioEventoMs = 0L
    private var ultimoSobreUmbralMs = 0L
    private var pgaPico = 0f
    private var pgaPicoBruto = 0f

    private var ruidoBase = 0f
    private var sumaCalibracion = 0.0
    private var muestrasCalibracion = 0

    val umbral: Float
        get() = max(umbralMinimo, factorRuido * ruidoBase)

    fun procesar(magnitud: Float, ahoraMs: Long): Estado {
        if (inicioMs < 0) inicioMs = ahoraMs
        val dtMs = if (ultimoMs < 0) 0L else (ahoraMs - ultimoMs).coerceAtLeast(0L)
        ultimoMs = ahoraMs

        pgaFiltrado = alpha * magnitud + (1f - alpha) * pgaFiltrado
        var alertaNueva = false

        when (fase) {
            Fase.CALIBRANDO -> {
                sumaCalibracion += pgaFiltrado
                muestrasCalibracion++
                if (ahoraMs - inicioMs >= calibracionMs) {
                    ruidoBase = (sumaCalibracion / muestrasCalibracion).toFloat()
                    fase = Fase.NORMAL
                }
            }

            Fase.NORMAL -> {
                if (pgaFiltrado >= umbral) {
                    fase = Fase.EVALUANDO
                    inicioEventoMs = ahoraMs
                    ultimoSobreUmbralMs = ahoraMs
                    pgaPico = pgaFiltrado
                    pgaPicoBruto = magnitud
                } else {
                    // Seguir lentamente el ruido de fondo solo cuando no hay evento.
                    val beta = (dtMs / constanteRuidoMs).coerceIn(0f, 1f)
                    ruidoBase += (pgaFiltrado - ruidoBase) * beta
                }
            }

            Fase.EVALUANDO -> {
                if (pgaFiltrado >= umbral) {
                    ultimoSobreUmbralMs = ahoraMs
                    pgaPico = max(pgaPico, pgaFiltrado)
                    pgaPicoBruto = max(pgaPicoBruto, magnitud)
                }
                if (ahoraMs - ultimoSobreUmbralMs > toleranciaPausaMs) {
                    reiniciarEvento()
                } else if (ahoraMs - inicioEventoMs >= duracionVerificacionMs) {
                    fase = Fase.ALERTA
                    nivel = clasificar(pgaPico)
                    alertaNueva = true
                }
            }

            Fase.ALERTA -> {
                if (pgaFiltrado >= umbral) {
                    ultimoSobreUmbralMs = ahoraMs
                    pgaPico = max(pgaPico, pgaFiltrado)
                    pgaPicoBruto = max(pgaPicoBruto, magnitud)
                    val nuevoNivel = clasificar(pgaPico)
                    if (nuevoNivel > nivel) {
                        nivel = nuevoNivel
                        alertaNueva = true
                    }
                }
                if (ahoraMs - ultimoSobreUmbralMs > retencionAlertaMs) {
                    reiniciarEvento()
                }
            }
        }

        val enEvento = fase == Fase.EVALUANDO || fase == Fase.ALERTA
        val segundos = if (enEvento) ((ahoraMs - inicioEventoMs) / 1000L).toInt() else 0
        val duracionMovimiento = if (enEvento) ((ultimoSobreUmbralMs - inicioEventoMs) / 1000L).toInt() else 0

        return Estado(fase, nivel, pgaFiltrado, pgaPico, pgaPicoBruto, umbral, segundos, duracionMovimiento, alertaNueva)
    }

    /** Botón "restablecer": vuelve a vigilancia normal sin perder la calibración. */
    fun restablecer() {
        if (fase != Fase.CALIBRANDO) reiniciarEvento()
    }

    private fun reiniciarEvento() {
        fase = Fase.NORMAL
        nivel = Nivel.NINGUNO
        pgaPico = 0f
        pgaPicoBruto = 0f
    }

    companion object {
        fun clasificar(pgaPico: Float): Nivel = when {
            pgaPico >= 1.20f -> Nivel.FUERTE
            pgaPico >= 0.35f -> Nivel.MODERADO
            else -> Nivel.LEVE
        }
    }
}
