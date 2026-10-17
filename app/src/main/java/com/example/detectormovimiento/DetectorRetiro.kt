package com.example.detectormovimiento

import kotlin.math.acos
import kotlin.math.sqrt

/**
 * Detecta, con la dirección de la gravedad, cuándo sacan el teléfono (la "cabeza") de su
 * lugar en la estación y cuándo lo vuelven a colocar.
 *
 * - Al fijar la referencia se guarda hacia dónde apunta la gravedad con el teléfono colocado.
 * - Retirado: el teléfono se inclina más de [anguloRetiro] grados durante [msConfirmar].
 * - Colocado otra vez: vuelve a menos de [anguloColocado] grados y se queda quieto [msAsentar].
 *
 * Un sismo sacude el teléfono pero no lo gira 35 grados, así que no se confunde con un retiro.
 */
class DetectorRetiro(
    private val anguloRetiro: Double = 35.0,
    private val anguloColocado: Double = 12.0,
    private val msConfirmar: Long = 800L,
    private val msAsentar: Long = 3_000L,
) {
    enum class Evento { RETIRADO, COLOCADO }

    var retirado = false
        private set

    private val gravedad = DoubleArray(3)
    private var hayGravedad = false
    private var referencia: DoubleArray? = null
    private var desdeMs = -1L

    val tieneReferencia get() = referencia != null

    fun procesar(x: Float, y: Float, z: Float, ahoraMs: Long): Evento? {
        val alfa = 0.1
        if (!hayGravedad) {
            gravedad[0] = x.toDouble(); gravedad[1] = y.toDouble(); gravedad[2] = z.toDouble()
            hayGravedad = true
        } else {
            gravedad[0] += alfa * (x - gravedad[0])
            gravedad[1] += alfa * (y - gravedad[1])
            gravedad[2] += alfa * (z - gravedad[2])
        }
        val ref = referencia ?: return null
        val angulo = anguloEntre(gravedad, ref) ?: return null

        val fueraDeLugar = if (retirado) angulo >= anguloColocado else angulo > anguloRetiro
        val cambiando = if (retirado) !fueraDeLugar else fueraDeLugar
        if (!cambiando) {
            desdeMs = -1L
            return null
        }
        if (desdeMs < 0) desdeMs = ahoraMs
        val espera = if (retirado) msAsentar else msConfirmar
        if (ahoraMs - desdeMs < espera) return null

        desdeMs = -1L
        retirado = !retirado
        return if (retirado) Evento.RETIRADO else Evento.COLOCADO
    }

    /** Guarda la posición actual como "colocado en la estación". */
    fun fijarReferencia() {
        if (!hayGravedad) return
        referencia = gravedad.copyOf()
        retirado = false
        desdeMs = -1L
    }

    fun olvidarReferencia() {
        referencia = null
        retirado = false
        desdeMs = -1L
    }

    companion object {
        fun anguloEntre(a: DoubleArray, b: DoubleArray): Double? {
            val na = sqrt(a[0] * a[0] + a[1] * a[1] + a[2] * a[2])
            val nb = sqrt(b[0] * b[0] + b[1] * b[1] + b[2] * b[2])
            if (na < 1e-6 || nb < 1e-6) return null
            val coseno = ((a[0] * b[0] + a[1] * b[1] + a[2] * b[2]) / (na * nb)).coerceIn(-1.0, 1.0)
            return Math.toDegrees(acos(coseno))
        }
    }
}
