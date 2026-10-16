package com.example.detectormovimiento

import android.app.Activity
import android.content.res.ColorStateList
import android.graphics.Color
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.media.AudioManager
import android.media.ToneGenerator
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.view.View
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.TextView
import com.example.detectormovimiento.AnalizadorVital.EstadoMedicion
import com.example.detectormovimiento.DetectorSismico.Fase
import com.example.detectormovimiento.DetectorSismico.Nivel
import java.util.Locale
import kotlin.math.abs
import kotlin.math.sqrt

class MainActivity : Activity(), SensorEventListener {

    private lateinit var sensorManager: SensorManager
    private var linearAccelSensor: Sensor? = null
    private var accelSensor: Sensor? = null
    private var isLinearAvailable = false

    // Tabs & Sections
    private lateinit var btnTabSeismic: Button
    private lateinit var btnTabBiometric: Button
    private lateinit var layoutSeismicSection: LinearLayout
    private lateinit var layoutBiometricSection: LinearLayout

    // Seismic UI Views
    private lateinit var tvStatusBadge: TextView
    private lateinit var tvNivelMovimiento: TextView
    private lateinit var tvAceleracionPico: TextView
    private lateinit var tvQuake30sTimer: TextView
    private lateinit var tvMercalliDetalle: TextView
    private lateinit var btnRestablecer: Button
    private lateinit var sismografoView: SismografoView

    // Biometric UI Views
    private lateinit var btnStartBiometric: Button
    private lateinit var pbBiometricProgress: ProgressBar
    private lateinit var tvBiometricStatus: TextView
    private lateinit var tvRpmEstado: TextView
    private lateinit var tvRpmValues: TextView
    private lateinit var tvBpmEstado: TextView
    private lateinit var tvBpmValues: TextView

    private var toneGenerator: ToneGenerator? = null
    private val handler = Handler(Looper.getMainLooper())

    // Sismología
    private val detector = DetectorSismico()
    private var ultimaFase: Fase? = null
    private var ultimoNivel = Nivel.NINGUNO
    private var ultimaActualizacionUi = 0L
    private var alarmaSonando = false

    // Biometría (20 s, acelerómetro a 200 Hz mientras se mide)
    private var isBiometricMeasuring = false
    private var biometricStartTime = 0L
    private val tiemposNs = ArrayList<Long>()
    private val muestrasX = ArrayList<Float>()
    private val muestrasY = ArrayList<Float>()
    private val muestrasZ = ArrayList<Float>()
    private var biometricProgressRunnable: Runnable? = null

    private val alarmaRunnable = object : Runnable {
        override fun run() {
            if (!alarmaSonando) return
            playSeismicTone(ultimoNivel)
            // LEVE: un solo aviso. MODERADO y FUERTE: la sirena se repite hasta restablecer.
            if (ultimoNivel == Nivel.MODERADO || ultimoNivel == Nivel.FUERTE) {
                handler.postDelayed(this, if (ultimoNivel == Nivel.FUERTE) 700L else 1500L)
            } else {
                alarmaSonando = false
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        // Initialize Tab Buttons & Section Views
        btnTabSeismic = findViewById(R.id.btnTabSeismic)
        btnTabBiometric = findViewById(R.id.btnTabBiometric)
        layoutSeismicSection = findViewById(R.id.layoutSeismicSection)
        layoutBiometricSection = findViewById(R.id.layoutBiometricSection)

        // Initialize Seismic Views
        tvStatusBadge = findViewById(R.id.tvStatusBadge)
        tvNivelMovimiento = findViewById(R.id.tvNivelMovimiento)
        tvAceleracionPico = findViewById(R.id.tvAceleracionPico)
        tvQuake30sTimer = findViewById(R.id.tvQuake30sTimer)
        tvMercalliDetalle = findViewById(R.id.tvMercalliDetalle)
        btnRestablecer = findViewById(R.id.btnRestablecer)
        sismografoView = findViewById(R.id.sismografoView)

        // Initialize Biometric Views
        btnStartBiometric = findViewById(R.id.btnStartBiometric)
        pbBiometricProgress = findViewById(R.id.pbBiometricProgress)
        tvBiometricStatus = findViewById(R.id.tvBiometricStatus)
        tvRpmEstado = findViewById(R.id.tvRpmEstado)
        tvRpmValues = findViewById(R.id.tvRpmValues)
        tvBpmEstado = findViewById(R.id.tvBpmEstado)
        tvBpmValues = findViewById(R.id.tvBpmValues)

        sensorManager = getSystemService(SENSOR_SERVICE) as SensorManager

        linearAccelSensor = sensorManager.getDefaultSensor(Sensor.TYPE_LINEAR_ACCELERATION)
        isLinearAvailable = linearAccelSensor != null
        accelSensor = sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)

        toneGenerator = try {
            ToneGenerator(AudioManager.STREAM_ALARM, 100)
        } catch (_: Exception) {
            null
        }

        if (accelSensor == null && linearAccelSensor == null) {
            tvStatusBadge.text = "⚠️ ESTE TELÉFONO NO TIENE ACELERÓMETRO"
            tvStatusBadge.setTextColor(Color.parseColor("#FF5252"))
            btnStartBiometric.isEnabled = false
        }

        // Tab click listeners
        btnTabSeismic.setOnClickListener {
            layoutSeismicSection.visibility = View.VISIBLE
            layoutBiometricSection.visibility = View.GONE
            pintarTab(btnTabSeismic, "#2563EB", true)
            pintarTab(btnTabBiometric, "#334155", false)
        }

        btnTabBiometric.setOnClickListener {
            layoutSeismicSection.visibility = View.GONE
            layoutBiometricSection.visibility = View.VISIBLE
            pintarTab(btnTabBiometric, "#0284C7", true)
            pintarTab(btnTabSeismic, "#334155", false)
        }

        btnRestablecer.setOnClickListener {
            detenerAlarma()
            detector.restablecer()
            ultimaFase = null
        }

        // Biometric Manual Start Button
        btnStartBiometric.setOnClickListener {
            startManualBiometricMeasurement()
        }
    }

    private fun pintarTab(boton: Button, colorFondo: String, activo: Boolean) {
        boton.backgroundTintList = ColorStateList.valueOf(Color.parseColor(colorFondo))
        boton.setTextColor(if (activo) Color.WHITE else Color.parseColor("#94A3B8"))
    }

    // ===================== SISMOLOGÍA =====================

    private fun processSeismicSensor(event: SensorEvent) {
        val x = event.values[0]
        val y = event.values[1]
        val z = event.values[2]
        val total = sqrt(x * x + y * y + z * z)
        // Sin sensor de aceleración lineal, se resta la gravedad a la magnitud.
        val magnitud = if (isLinearAvailable) total else abs(total - SensorManager.GRAVITY_EARTH)

        val ahora = SystemClock.elapsedRealtime()
        val estado = detector.procesar(magnitud, ahora)

        sismografoView.agregarPunto(estado.pga)

        if (estado.alertaNueva) {
            ultimoNivel = estado.nivel
            iniciarAlarma()
        }

        // La interfaz se actualiza 10 veces por segundo (o al cambiar de fase) para no
        // trabar los teléfonos viejos; el sensor llega a unas 50 muestras por segundo.
        if (estado.fase == ultimaFase && !estado.alertaNueva && ahora - ultimaActualizacionUi < 100L) return
        ultimaActualizacionUi = ahora
        if (estado.fase != ultimaFase && estado.fase != Fase.ALERTA) detenerAlarma()
        ultimaFase = estado.fase
        mostrarEstadoSismico(estado)
    }

    private fun mostrarEstadoSismico(estado: DetectorSismico.Estado) {
        val color: String
        when (estado.fase) {
            Fase.CALIBRANDO -> {
                color = "#38BDF8"
                tvStatusBadge.text = "◌ CALIBRANDO SENSOR..."
                tvNivelMovimiento.text = "Deja el teléfono quieto unos segundos"
                tvQuake30sTimer.text = "Midiendo el ruido propio del sensor de este teléfono."
                tvMercalliDetalle.text = "Intensidad: I - Imperceptible. Sistema iniciando."
                btnRestablecer.visibility = View.GONE
            }

            Fase.NORMAL -> {
                color = "#00E676"
                tvStatusBadge.text = "● SISTEMA NORMAL"
                tvNivelMovimiento.text = "Sin Actividad Sísmica Sostenida"
                tvQuake30sTimer.text = String.format(
                    Locale.getDefault(),
                    "⏳ Vigilando. Umbral de este teléfono: %.2f m/s²", estado.umbral
                )
                tvMercalliDetalle.text = "Intensidad: I - Imperceptible. Sistema monitoreando."
                btnRestablecer.visibility = View.GONE
            }

            Fase.EVALUANDO -> {
                color = "#FFA726"
                val segundos = estado.segundosEvaluados.coerceAtMost(10)
                tvStatusBadge.text = String.format(
                    Locale.getDefault(), "⏳ EVALUANDO MOVIMIENTO SOSTENIDO (%ds / 10s)", segundos
                )
                tvNivelMovimiento.text = "Movimiento en verificación..."
                tvQuake30sTimer.text = String.format(
                    Locale.getDefault(),
                    "⏳ Verificando si el movimiento persiste por 10s: %ds transcurridos", segundos
                )
                btnRestablecer.visibility = View.GONE
            }

            Fase.ALERTA -> {
                val textos = when (estado.nivel) {
                    Nivel.FUERTE -> Triple(
                        "🚨 ALERTA MÁXIMA: SISMO FUERTE DETECTADO",
                        "Sacudida Intensa Sostenida",
                        "Intensidad: VI+ - Fuerte / Destructivo. Agáchate, cúbrete y sujétate. Al terminar, evacua."
                    )
                    Nivel.MODERADO -> Triple(
                        "▲ SISMO MODERADO DETECTADO",
                        "Movimiento Moderado Sostenido",
                        "Intensidad: IV - V - Moderado. Agáchate, cúbrete y sujétate."
                    )
                    else -> Triple(
                        "▲ SISMO LEVE DETECTADO",
                        "Movimiento Leve Sostenido",
                        "Intensidad: II - III - Leve. Mantén la calma y aléjate de ventanas."
                    )
                }
                color = when (estado.nivel) {
                    Nivel.FUERTE -> "#FF5252"
                    Nivel.MODERADO -> "#FFA726"
                    else -> "#29B6F6"
                }
                tvStatusBadge.text = textos.first
                tvNivelMovimiento.text = textos.second
                tvMercalliDetalle.text = textos.third
                tvQuake30sTimer.text = String.format(
                    Locale.getDefault(),
                    "✅ Sismo verificado (%ds de movimiento). Pico: %.2f m/s²",
                    estado.segundosEvaluados, estado.pgaPico
                )
                btnRestablecer.visibility = View.VISIBLE
            }
        }

        val c = Color.parseColor(color)
        sismografoView.setStrokeColor(color)
        tvStatusBadge.setTextColor(c)
        tvAceleracionPico.setTextColor(c)
        tvAceleracionPico.text = String.format(Locale.getDefault(), "PGA Inmediato: %.2f m/s²", estado.pga)
    }

    private fun iniciarAlarma() {
        handler.removeCallbacks(alarmaRunnable)
        alarmaSonando = true
        handler.post(alarmaRunnable)
    }

    private fun detenerAlarma() {
        alarmaSonando = false
        handler.removeCallbacks(alarmaRunnable)
        toneGenerator?.stopTone()
    }

    private fun playSeismicTone(nivel: Nivel) {
        try {
            when (nivel) {
                Nivel.LEVE -> toneGenerator?.startTone(ToneGenerator.TONE_PROP_BEEP2, 400)
                Nivel.MODERADO -> toneGenerator?.startTone(ToneGenerator.TONE_CDMA_ALERT_CALL_GUARD, 600)
                Nivel.FUERTE -> toneGenerator?.startTone(ToneGenerator.TONE_CDMA_EMERGENCY_RINGBACK, 650)
                Nivel.NINGUNO -> Unit
            }
        } catch (_: Exception) {
            // Ignorar: sin audio la alerta sigue visible en pantalla
        }
    }

    // ===================== SIGNOS VITALES =====================

    private fun startManualBiometricMeasurement() {
        if (isBiometricMeasuring) return
        val sensor = accelSensor ?: return

        isBiometricMeasuring = true
        biometricStartTime = SystemClock.elapsedRealtime()
        tiemposNs.clear()
        muestrasX.clear()
        muestrasY.clear()
        muestrasZ.clear()

        // Más muestras por segundo para poder ver la vibración de cada latido.
        sensorManager.unregisterListener(this, sensor)
        sensorManager.registerListener(this, sensor, PERIODO_BIOMETRIA_US)

        btnStartBiometric.isEnabled = false
        pbBiometricProgress.visibility = View.VISIBLE
        pbBiometricProgress.progress = 0
        tvBiometricStatus.text = "⏳ Midiendo... Quédate quieto con el teléfono sobre el pecho (20s)"
        tvBiometricStatus.setTextColor(Color.parseColor("#F8FAFC"))
        mostrarValor(tvRpmValues, tvRpmEstado, "-- RPM", "Midiendo...", "#38BDF8")
        mostrarValor(tvBpmValues, tvBpmEstado, "-- BPM", "Midiendo...", "#F43F5E")

        biometricProgressRunnable = object : Runnable {
            override fun run() {
                if (!isBiometricMeasuring) return
                val elapsed = SystemClock.elapsedRealtime() - biometricStartTime
                pbBiometricProgress.progress = ((elapsed * 100) / DURACION_BIOMETRIA_MS).toInt().coerceIn(0, 100)

                if (elapsed >= DURACION_BIOMETRIA_MS) {
                    finishBiometricMeasurement()
                } else {
                    handler.postDelayed(this, 100)
                }
            }
        }
        handler.post(biometricProgressRunnable!!)
    }

    private fun detenerCapturaBiometrica() {
        isBiometricMeasuring = false
        biometricProgressRunnable?.let { handler.removeCallbacks(it) }
        pbBiometricProgress.visibility = View.GONE
        btnStartBiometric.isEnabled = true
        // Volver a la frecuencia normal para ahorrar batería.
        accelSensor?.let {
            sensorManager.unregisterListener(this, it)
            sensorManager.registerListener(this, it, SensorManager.SENSOR_DELAY_GAME)
        }
    }

    private fun finishBiometricMeasurement() {
        detenerCapturaBiometrica()

        val resultado = AnalizadorVital.analizar(
            tiemposNs.toLongArray(),
            muestrasX.toFloatArray(),
            muestrasY.toFloatArray(),
            muestrasZ.toFloatArray(),
        )

        val rojo = "#FF5252"
        when (resultado.estado) {
            EstadoMedicion.MUESTRA_INSUFICIENTE -> {
                tvBiometricStatus.text = "⚠️ Muestra insuficiente. Intenta de nuevo."
                tvBiometricStatus.setTextColor(Color.parseColor(rojo))
                mostrarValor(tvRpmValues, tvRpmEstado, "-- RPM", "Sin medición", rojo)
                mostrarValor(tvBpmValues, tvBpmEstado, "-- BPM", "Sin medición", rojo)
                return
            }

            EstadoMedicion.SIN_MOVIMIENTO -> {
                tvBiometricStatus.text = "⚠️ No se detectó movimiento de pecho. ¿Está el teléfono sobre una mesa u objeto sólido?"
                tvBiometricStatus.setTextColor(Color.parseColor(rojo))
                mostrarValor(tvRpmValues, tvRpmEstado, "0 RPM", "Sin movimiento", rojo)
                mostrarValor(tvBpmValues, tvBpmEstado, "-- BPM", "Sin señal", rojo)
                return
            }

            EstadoMedicion.MUCHO_MOVIMIENTO -> {
                tvBiometricStatus.text = "⚠️ Hubo demasiado movimiento. Recuéstate quieto y repite la medición."
                tvBiometricStatus.setTextColor(Color.parseColor(rojo))
                mostrarValor(tvRpmValues, tvRpmEstado, "-- RPM", "Repetir", rojo)
                mostrarValor(tvBpmValues, tvBpmEstado, "-- BPM", "Repetir", rojo)
                return
            }

            EstadoMedicion.RESPIRACION_IRREGULAR, EstadoMedicion.OK -> Unit
        }

        // Frecuencia respiratoria
        val rpm = resultado.rpm
        if (rpm == null) {
            mostrarValor(tvRpmValues, tvRpmEstado, "-- RPM", "Irregular, no se pudo medir", "#FFA726")
        } else {
            val (rpmStatus, rpmColor) = when {
                rpm < 12 -> Pair("Bradipnea (Lenta)", "#38BDF8")
                rpm <= 20 -> Pair("Eupnea (Normal)", "#00E676")
                else -> Pair("Taquipnea (Acelerada)", rojo)
            }
            mostrarValor(tvRpmValues, tvRpmEstado, String.format(Locale.getDefault(), "%d RPM", rpm), rpmStatus, rpmColor)
        }

        // Ritmo cardíaco
        val bpm = resultado.bpm
        if (bpm == null) {
            val motivo = if (resultado.frecuenciaMuestreoHz < 40.0) "Sensor muy lento en este teléfono" else "Señal débil, tómalo en la muñeca"
            mostrarValor(tvBpmValues, tvBpmEstado, "-- BPM", motivo, "#FFA726")
        } else {
            val (bpmStatus, bpmColor) = when {
                bpm < 60 -> Pair("Bradicardia (Lento)", "#38BDF8")
                bpm <= 100 -> Pair("Pulso Normal", "#00E676")
                else -> Pair("Taquicardia (Acelerado)", rojo)
            }
            mostrarValor(tvBpmValues, tvBpmEstado, String.format(Locale.getDefault(), "%d BPM", bpm), bpmStatus, bpmColor)
        }

        if (rpm != null && bpm != null) {
            tvBiometricStatus.text = "✅ Medición completada. Valores orientativos."
            tvBiometricStatus.setTextColor(Color.parseColor("#00E676"))
        } else {
            tvBiometricStatus.text = "⚠️ Medición parcial: algún valor no fue confiable."
            tvBiometricStatus.setTextColor(Color.parseColor("#FFA726"))
        }
    }

    private fun mostrarValor(valor: TextView, estado: TextView, texto: String, textoEstado: String, color: String) {
        val c = Color.parseColor(color)
        valor.text = texto
        valor.setTextColor(c)
        estado.text = textoEstado
        estado.setTextColor(c)
    }

    // ===================== CICLO DE VIDA Y SENSORES =====================

    override fun onResume() {
        super.onResume()
        linearAccelSensor?.let {
            sensorManager.registerListener(this, it, SensorManager.SENSOR_DELAY_GAME)
        }
        accelSensor?.let {
            sensorManager.registerListener(this, it, SensorManager.SENSOR_DELAY_GAME)
        }
    }

    override fun onPause() {
        super.onPause()
        sensorManager.unregisterListener(this)
        detenerAlarma()
        if (isBiometricMeasuring) {
            isBiometricMeasuring = false
            biometricProgressRunnable?.let { handler.removeCallbacks(it) }
            pbBiometricProgress.visibility = View.GONE
            btnStartBiometric.isEnabled = true
            tvBiometricStatus.text = "Medición cancelada. Toca para empezar de nuevo."
            tvBiometricStatus.setTextColor(Color.parseColor("#F8FAFC"))
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        handler.removeCallbacksAndMessages(null)
        toneGenerator?.release()
        toneGenerator = null
    }

    override fun onSensorChanged(event: SensorEvent?) {
        if (event == null) return

        val sensorType = event.sensor.type

        // 1. SISMOLOGÍA
        if ((isLinearAvailable && sensorType == Sensor.TYPE_LINEAR_ACCELERATION) ||
            (!isLinearAvailable && sensorType == Sensor.TYPE_ACCELEROMETER)
        ) {
            processSeismicSensor(event)
        }

        // 2. CAPTURA DE MUESTRAS PARA BIOMETRÍA (los tres ejes, con su hora exacta)
        if (sensorType == Sensor.TYPE_ACCELEROMETER && isBiometricMeasuring) {
            tiemposNs.add(event.timestamp)
            muestrasX.add(event.values[0])
            muestrasY.add(event.values[1])
            muestrasZ.add(event.values[2])
        }
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {
        // No usado
    }

    private companion object {
        const val DURACION_BIOMETRIA_MS = 20_000L
        const val PERIODO_BIOMETRIA_US = 5_000 // 200 Hz (el máximo permitido sin permisos extra)
    }
}
