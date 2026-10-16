package com.example.detectormovimiento

import android.app.Activity
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
import android.view.View
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.TextView
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
    private var lastToneTime = 0L

    // Seismic Low-Pass Filter (alpha = 0.15f)
    private var filteredPga = 0f
    private val alphaPga = 0.15f
    private var currentPgaColor = "#00E676"

    // 10-Second Continuous Sustained Motion Evaluation
    private var quakeStartTimestamp = 0L
    private var is10sAlertTriggered = false
    private val pgaHistory10s = mutableListOf<PgaSample>()

    // Biometric measurement variables (15-second session)
    private var isBiometricMeasuring = false
    private var biometricStartTime = 0L
    private val zRawSamples = mutableListOf<Float>()
    private val handler = Handler(Looper.getMainLooper())
    private var biometricProgressRunnable: Runnable? = null

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
        if (linearAccelSensor != null) {
            isLinearAvailable = true
        }
        accelSensor = sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)

        toneGenerator = try {
            ToneGenerator(AudioManager.STREAM_ALARM, 100)
        } catch (_: Exception) {
            null
        }

        // Tab click listeners
        btnTabSeismic.setOnClickListener {
            layoutSeismicSection.visibility = View.VISIBLE
            layoutBiometricSection.visibility = View.GONE
            btnTabSeismic.setBackgroundColor(Color.parseColor("#2563EB"))
            btnTabSeismic.setTextColor(Color.WHITE)
            btnTabBiometric.setBackgroundColor(Color.parseColor("#334155"))
            btnTabBiometric.setTextColor(Color.parseColor("#94A3B8"))
        }

        btnTabBiometric.setOnClickListener {
            layoutSeismicSection.visibility = View.GONE
            layoutBiometricSection.visibility = View.VISIBLE
            btnTabBiometric.setBackgroundColor(Color.parseColor("#0284C7"))
            btnTabBiometric.setTextColor(Color.WHITE)
            btnTabSeismic.setBackgroundColor(Color.parseColor("#334155"))
            btnTabSeismic.setTextColor(Color.parseColor("#94A3B8"))
        }

        // Biometric Manual Start Button
        btnStartBiometric.setOnClickListener {
            startManualBiometricMeasurement()
        }
    }

    private fun startManualBiometricMeasurement() {
        if (isBiometricMeasuring) return

        isBiometricMeasuring = true
        biometricStartTime = System.currentTimeMillis()
        zRawSamples.clear()

        btnStartBiometric.isEnabled = false
        pbBiometricProgress.visibility = View.VISIBLE
        pbBiometricProgress.progress = 0
        tvBiometricStatus.text = "⏳ Midiendo... Mantén el teléfono quieto sobre tu pecho (15s)"
        tvBiometricStatus.setTextColor(Color.parseColor("#F8FAFC"))
        tvRpmValues.text = "0 RPM"
        tvRpmEstado.text = "Midiendo..."
        tvBpmValues.text = "0 BPM"
        tvBpmEstado.text = "Midiendo..."

        biometricProgressRunnable = object : Runnable {
            override fun run() {
                if (!isBiometricMeasuring) return
                val elapsed = System.currentTimeMillis() - biometricStartTime
                val progress = ((elapsed * 100) / 15000L).toInt().coerceIn(0, 100)
                pbBiometricProgress.progress = progress

                if (elapsed >= 15000L) {
                    finishBiometricMeasurement()
                } else {
                    handler.postDelayed(this, 100)
                }
            }
        }
        handler.post(biometricProgressRunnable!!)
    }

    private fun finishBiometricMeasurement() {
        isBiometricMeasuring = false
        btnStartBiometric.isEnabled = true
        pbBiometricProgress.visibility = View.GONE

        if (zRawSamples.size < 50) {
            tvBiometricStatus.text = "⚠️ Muestra insuficiente. Intenta de nuevo."
            tvBiometricStatus.setTextColor(Color.parseColor("#FF5252"))
            tvRpmValues.text = "0 RPM"
            tvBpmValues.text = "0 BPM"
            return
        }

        // 1. Compute Mean Z to remove DC offset / gravity constant
        val meanZ = zRawSamples.average().toFloat()

        // 2. Compute AC Variance / Standard Deviation of Z
        var varianceSum = 0.0
        val acZList = mutableListOf<Float>()
        for (raw in zRawSamples) {
            val ac = raw - meanZ
            acZList.add(ac)
            varianceSum += (ac * ac)
        }
        val stdDev = sqrt(varianceSum / zRawSamples.size).toFloat()

        // 3. Low-Pass Filter AC signal for chest expansion (alpha = 0.08f)
        val filteredAcZ = mutableListOf<Float>()
        var fz = acZList[0]
        for (ac in acZList) {
            fz = 0.08f * ac + 0.92f * fz
            filteredAcZ.add(fz)
        }

        val maxAc = filteredAcZ.maxOrNull() ?: 0f
        val minAc = filteredAcZ.minOrNull() ?: 0f
        val chestP2P = maxAc - minAc

        // RIGID TABLE / SOLID OBJECT CHECK:
        // On a solid table, stdDev is < 0.025 m/s² and chest peak-to-peak amplitude < 0.035 m/s²
        if (stdDev < 0.025f || chestP2P < 0.035f) {
            tvBiometricStatus.text = "⚠️ Dispositivo sobre objeto sólido / mesa. No se detectó movimiento de pecho (0 RPM / 0 BPM)."
            tvBiometricStatus.setTextColor(Color.parseColor("#FF5252"))

            tvRpmEstado.text = "Objeto sólido (Sin movimiento)"
            tvRpmEstado.setTextColor(Color.parseColor("#FF5252"))
            tvRpmValues.text = "0 RPM"
            tvRpmValues.setTextColor(Color.parseColor("#FF5252"))

            tvBpmEstado.text = "Objeto sólido (Sin pulso)"
            tvBpmEstado.setTextColor(Color.parseColor("#FF5252"))
            tvBpmValues.text = "0 BPM"
            tvBpmValues.setTextColor(Color.parseColor("#FF5252"))
            return
        }

        // Process chest inhalation peaks and heart micro-pulses
        var breathPeaks = 0
        var pulsePeaks = 0
        var prevVal = filteredAcZ[0]
        var prevTrend = 0

        for (i in 1 until filteredAcZ.size) {
            val curr = filteredAcZ[i]
            val diff = curr - prevVal
            val trend = if (diff > 0.0020f) 1 else if (diff < -0.0020f) -1 else 0

            if (prevTrend == 1 && trend == -1) {
                if (curr > 0.015f) {
                    breathPeaks++
                }
                if (curr in 0.003f..0.014f) {
                    pulsePeaks++
                }
            }
            prevVal = curr
            prevTrend = trend
        }

        // IF NO VALID CHEST PEAKS DETECTED -> RESULT IS 0
        if (breathPeaks < 2) {
            tvBiometricStatus.text = "⚠️ Objeto sólido / Movimiento de pecho no detectado."
            tvBiometricStatus.setTextColor(Color.parseColor("#FF5252"))

            tvRpmEstado.text = "Sin movimiento"
            tvRpmEstado.setTextColor(Color.parseColor("#FF5252"))
            tvRpmValues.text = "0 RPM"
            tvRpmValues.setTextColor(Color.parseColor("#FF5252"))

            tvBpmEstado.text = "Sin pulso"
            tvBpmEstado.setTextColor(Color.parseColor("#FF5252"))
            tvBpmValues.text = "0 BPM"
            tvBpmValues.setTextColor(Color.parseColor("#FF5252"))
            return
        }

        // Calculate RPM and BPM for 15s session
        val rpm = (breathPeaks * 4).coerceIn(8, 32)
        val bpm = if (pulsePeaks > 0) ((pulsePeaks * 4) + 56).coerceIn(58, 120) else 0

        // Respiration Status
        val (rpmStatus, rpmColor) = when {
            rpm == 0 -> Pair("Sin movimiento", "#FF5252")
            rpm < 10 -> Pair("Bradipnea (Lenta)", "#38BDF8")
            rpm in 10..20 -> Pair("Eupnea (Normal)", "#00E676")
            else -> Pair("Taquipnea (Acelerada)", "#FF5252")
        }
        tvRpmValues.text = String.format(Locale.getDefault(), "%d RPM", rpm)
        tvRpmEstado.text = rpmStatus
        tvRpmEstado.setTextColor(Color.parseColor(rpmColor))
        tvRpmValues.setTextColor(Color.parseColor(rpmColor))

        // Heart Rate Status
        val (bpmStatus, bpmColor) = when {
            bpm == 0 -> Pair("Sin pulso detectado", "#FF5252")
            bpm < 60 -> Pair("Bradicardia (Lento)", "#38BDF8")
            bpm in 60..100 -> Pair("Pulso Normal", "#00E676")
            else -> Pair("Taquicardia (Acelerado)", "#FF5252")
        }
        tvBpmValues.text = String.format(Locale.getDefault(), "%d BPM", bpm)
        tvBpmEstado.text = bpmStatus
        tvBpmEstado.setTextColor(Color.parseColor(bpmColor))
        tvBpmValues.setTextColor(Color.parseColor(bpmColor))

        tvBiometricStatus.text = "✅ Medición sobre el pecho completada con éxito."
        tvBiometricStatus.setTextColor(Color.parseColor("#00E676"))
    }

    override fun onResume() {
        super.onResume()
        if (isLinearAvailable) {
            linearAccelSensor?.let {
                sensorManager.registerListener(this, it, SensorManager.SENSOR_DELAY_GAME)
            }
        }
        accelSensor?.let {
            sensorManager.registerListener(this, it, SensorManager.SENSOR_DELAY_GAME)
        }
    }

    override fun onPause() {
        super.onPause()
        sensorManager.unregisterListener(this)
        if (isBiometricMeasuring) {
            isBiometricMeasuring = false
            biometricProgressRunnable?.let { handler.removeCallbacks(it) }
            pbBiometricProgress.visibility = View.GONE
            btnStartBiometric.isEnabled = true
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        toneGenerator?.release()
        toneGenerator = null
    }

    override fun onSensorChanged(event: SensorEvent?) {
        if (event == null) return

        val sensorType = event.sensor.type

        // 1. SISMOLOGÍA (Verificación de Movimiento Sostenido durante 10 Segundos)
        if ((isLinearAvailable && sensorType == Sensor.TYPE_LINEAR_ACCELERATION) ||
            (!isLinearAvailable && sensorType == Sensor.TYPE_ACCELEROMETER)
        ) {
            processSeismicSensor(event)
        }

        // 2. CAPTURA DE MUESTRAS Z PARA BIOMETRÍA
        if (sensorType == Sensor.TYPE_ACCELEROMETER && isBiometricMeasuring) {
            zRawSamples.add(event.values[2])
        }
    }

    private fun processSeismicSensor(event: SensorEvent) {
        val x = event.values[0]
        val y = event.values[1]
        val z = event.values[2]

        val rawMag: Float = if (isLinearAvailable) {
            sqrt((x * x + y * y + z * z).toDouble()).toFloat()
        } else {
            val totalMag = sqrt((x * x + y * y + z * z).toDouble()).toFloat()
            abs(totalMag - SensorManager.GRAVITY_EARTH)
        }

        // Filtro pasa-bajas (alpha = 0.15f)
        filteredPga = alphaPga * rawMag + (1f - alphaPga) * filteredPga

        val now = System.currentTimeMillis()

        // Actualizar sismograma en tiempo real
        sismografoView.agregarPunto(filteredPga)

        // Detección de movimiento sísmico activo
        val isMotionActive = filteredPga >= 0.05f

        if (isMotionActive) {
            if (quakeStartTimestamp == 0L) {
                quakeStartTimestamp = now
                is10sAlertTriggered = false
            }

            val sustainedDurationMs = now - quakeStartTimestamp
            val sustainedSec = (sustainedDurationMs / 1000L).coerceAtMost(10L).toInt()

            pgaHistory10s.add(PgaSample(now, filteredPga))
            pgaHistory10s.removeAll { now - it.timestamp > 10000L }

            val pgaPeak10s = pgaHistory10s.maxOfOrNull { it.pga } ?: filteredPga

            if (sustainedSec < 10) {
                // Durante los 10s de movimiento sostenido:
                currentPgaColor = "#FFA726"
                sismografoView.setStrokeColor(currentPgaColor)
                tvStatusBadge.text = String.format(Locale.getDefault(), "⏳ EVALUANDO MOVIMIENTO SOSTENIDO (%ds / 10s)", sustainedSec)
                tvStatusBadge.setTextColor(Color.parseColor(currentPgaColor))
                tvAceleracionPico.setTextColor(Color.parseColor(currentPgaColor))
                tvNivelMovimiento.text = "Movimiento en verificación..."
                tvQuake30sTimer.text = String.format(Locale.getDefault(), "⏳ Verificando si el movimiento persiste por 10s: %ds transcurridos", sustainedSec)
            } else {
                // ¡10 SEGUNDOS CUMPLIDOS DE MOVIMIENTO CONTINUO! -> Emitir clasificación de alerta
                val (badgeText, movementText, mercalliText, colorHex, soundLevel) = when {
                    pgaPeak10s >= 1.20f -> Quint(
                        "🚨 ALERTA MÁXIMA: SISMO FUERTE DETECTADO",
                        "Sacudida Intensa Sostenida (10s)",
                        "Intensidad: VI+ - Fuerte / Destructivo. Movimiento verificado por 10s. ¡Evacua!",
                        "#FF5252",
                        3
                    )
                    pgaPeak10s >= 0.35f -> Quint(
                        "▲ SISMO MODERADO DETECTADO",
                        "Movimiento Moderado Sostenido (10s)",
                        "Intensidad: IV - V - Moderado. Movimiento verificado por 10s.",
                        "#FFA726",
                        2
                    )
                    else -> Quint(
                        "▲ SISMO LEVE DETECTADO",
                        "Movimiento Leve Sostenido (10s)",
                        "Intensidad: II - III - Leve. Movimiento verificado por 10s.",
                        "#29B6F6",
                        1
                    )
                }

                currentPgaColor = colorHex
                sismografoView.setStrokeColor(colorHex)
                tvStatusBadge.text = badgeText
                tvStatusBadge.setTextColor(Color.parseColor(colorHex))
                tvAceleracionPico.setTextColor(Color.parseColor(colorHex))
                tvNivelMovimiento.text = movementText
                tvMercalliDetalle.text = mercalliText
                tvQuake30sTimer.text = "✅ Sismo sostenido verificado durante 10 segundos continuos."

                if (!is10sAlertTriggered) {
                    is10sAlertTriggered = true
                    playSeismicTone(soundLevel)
                }
            }
        } else {
            // El movimiento se detuvo antes de 10s -> Reiniciar
            quakeStartTimestamp = 0L
            is10sAlertTriggered = false
            pgaHistory10s.clear()

            currentPgaColor = "#00E676"
            sismografoView.setStrokeColor(currentPgaColor)
            tvStatusBadge.text = "● SISTEMA NORMAL"
            tvStatusBadge.setTextColor(Color.parseColor(currentPgaColor))
            tvAceleracionPico.setTextColor(Color.parseColor(currentPgaColor))
            tvNivelMovimiento.text = "Sin Actividad Sísmica Sostenida"
            tvQuake30sTimer.text = "⏳ Esperando detección de movimiento sostenido por 10s..."
            tvMercalliDetalle.text = "Intensidad: I - Imperceptible. Sistema monitoreando."
        }

        tvAceleracionPico.text = String.format(Locale.getDefault(), "PGA Inmediato: %.2f m/s²", filteredPga)
    }

    private fun playSeismicTone(level: Int) {
        val now = System.currentTimeMillis()
        if (now - lastToneTime < 1000L) return
        lastToneTime = now

        try {
            when (level) {
                1 -> toneGenerator?.startTone(ToneGenerator.TONE_PROP_BEEP, 150)
                2 -> toneGenerator?.startTone(ToneGenerator.TONE_CDMA_ALERT_CALL_GUARD, 250)
                3 -> toneGenerator?.startTone(ToneGenerator.TONE_CDMA_EMERGENCY_RINGBACK, 400)
            }
        } catch (_: Exception) {
            // Ignorar
        }
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {
        // No usado
    }

    private data class PgaSample(val timestamp: Long, val pga: Float)
    private data class Quint<A, B, C, D, E>(val first: A, val second: B, val third: C, val fourth: D, val fifth: E)
}