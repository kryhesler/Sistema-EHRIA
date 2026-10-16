package com.example.detectormovimiento

import android.app.Activity
import android.graphics.Color
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.os.Bundle
import android.widget.TextView
import java.util.Locale
import kotlin.math.abs
import kotlin.math.roundToInt
import kotlin.math.sqrt

class MainActivity : Activity(), SensorEventListener {

    private lateinit var sensorManager: SensorManager
    private var linearAccelSensor: Sensor? = null
    private var accelSensor: Sensor? = null
    private var isLinearAvailable = false

    // Seismic UI Views
    private lateinit var tvStatusBadge: TextView
    private lateinit var tvNivelMovimiento: TextView
    private lateinit var tvAceleracionPico: TextView
    private lateinit var tvMercalliDetalle: TextView
    private lateinit var sismografoView: SismografoView

    // Respiration UI Views
    private lateinit var tvRpmEstado: TextView
    private lateinit var tvRpmValues: TextView

    // Seismic Low-Pass Filter (alpha = 0.15f)
    private var filteredPga = 0f
    private val alphaPga = 0.15f
    private var currentPgaColor = "#00E676"

    // Respiration Low-Pass Filter (alpha = 0.08f)
    private var filteredZ = 0f
    private var isFirstRpmSample = true
    private val alphaRpm = 0.08f

    // Peak detection for Respiration
    private var prevFilteredZ = 0f
    private var isRising = false
    private var lastPeakTime = 0L
    private var minZInCycle = Float.MAX_VALUE
    private val peakTimestamps = mutableListOf<Long>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        // Initialize Seismic Views
        tvStatusBadge = findViewById(R.id.tvStatusBadge)
        tvNivelMovimiento = findViewById(R.id.tvNivelMovimiento)
        tvAceleracionPico = findViewById(R.id.tvAceleracionPico)
        tvMercalliDetalle = findViewById(R.id.tvMercalliDetalle)
        sismografoView = findViewById(R.id.sismografoView)

        // Initialize Respiration Views
        tvRpmEstado = findViewById(R.id.tvRpmEstado)
        tvRpmValues = findViewById(R.id.tvRpmValues)

        sensorManager = getSystemService(SENSOR_SERVICE) as SensorManager

        linearAccelSensor = sensorManager.getDefaultSensor(Sensor.TYPE_LINEAR_ACCELERATION)
        if (linearAccelSensor != null) {
            isLinearAvailable = true
        }

        accelSensor = sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)
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
    }

    override fun onSensorChanged(event: SensorEvent?) {
        if (event == null) return

        val sensorType = event.sensor.type

        // 1. MONITOREO SÍSMICO (Linear Acceleration or Accelerometer fallback)
        if ((isLinearAvailable && sensorType == Sensor.TYPE_LINEAR_ACCELERATION) ||
            (!isLinearAvailable && sensorType == Sensor.TYPE_ACCELEROMETER)
        ) {
            processSeismicSensor(event)
        }

        // 2. MONITOR DE FRECUENCIA RESPIRATORIA (Accelerometer Z Axis)
        if (sensorType == Sensor.TYPE_ACCELEROMETER) {
            processRespirationSensor(event)
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

        // Actualizar sismograma en tiempo real
        sismografoView.agregarPunto(filteredPga)

        // Clasificación de PGA en 4 niveles
        val (badgeText, movementText, mercalliText, colorHex) = when {
            filteredPga >= 1.20f -> Quad(
                "🚨 ALERTA MÁXIMA",
                "Sacudida Intensa",
                "Intensidad: VI+ - Fuerte / Destructivo. Dificultad para mantenerse en pie. ¡Cúbrete y evacua!",
                "#FF5252"
            )
            filteredPga >= 0.35f -> Quad(
                "▲ SISMO MODERADO",
                "Movimiento Moderado",
                "Intensidad: IV - V - Moderado. Sentido en interiores, objetos oscilan o caen levemente.",
                "#FFA726"
            )
            filteredPga >= 0.05f -> Quad(
                "▲ SISMO LEVE",
                "Movimiento Leve",
                "Intensidad: II - III - Leve. Sentido por pocas personas en reposo u objetos colgados.",
                "#29B6F6"
            )
            else -> Quad(
                "● SISTEMA NORMAL",
                "Sin Actividad Sísmica",
                "Intensidad: I - Imperceptible. Sin movimiento sísmico perceptible en el área.",
                "#00E676"
            )
        }

        if (colorHex != currentPgaColor) {
            currentPgaColor = colorHex
            sismografoView.setStrokeColor(colorHex)
            tvStatusBadge.setTextColor(Color.parseColor(colorHex))
            tvAceleracionPico.setTextColor(Color.parseColor(colorHex))
        }

        tvStatusBadge.text = badgeText
        tvNivelMovimiento.text = movementText
        tvAceleracionPico.text = String.format(Locale.getDefault(), "PGA: %.2f m/s²", filteredPga)
        tvMercalliDetalle.text = mercalliText
    }

    private fun processRespirationSensor(event: SensorEvent) {
        val rawZ = event.values[2]

        if (isFirstRpmSample) {
            filteredZ = rawZ
            prevFilteredZ = rawZ
            minZInCycle = rawZ
            isFirstRpmSample = false
            return
        }

        // Filtro pasa-bajas suave (alpha = 0.08f) para aislar la expansión del tórax
        filteredZ = alphaRpm * rawZ + (1f - alphaRpm) * filteredZ

        val now = System.currentTimeMillis()

        if (filteredZ < minZInCycle) {
            minZInCycle = filteredZ
        }

        val deltaZ = filteredZ - prevFilteredZ

        if (deltaZ > 0.003f) {
            isRising = true
        } else if (deltaZ < -0.003f && isRising) {
            // Pico detectado (inhalación máxima)
            isRising = false
            val amplitude = filteredZ - minZInCycle
            val timeSinceLastPeak = now - lastPeakTime

            // Criterios de respiración válida: tiempo >= 1200ms y amplitud mínima
            if (timeSinceLastPeak >= 1200L && amplitude > 0.025f) {
                lastPeakTime = now
                minZInCycle = filteredZ
                peakTimestamps.add(now)

                // Mantener ventana móvil de los últimos 30 segundos
                peakTimestamps.removeAll { now - it > 30000L }

                calculateAndDisplayRpm()
            }
        }

        prevFilteredZ = filteredZ
    }

    private fun calculateAndDisplayRpm() {
        if (peakTimestamps.size < 2) {
            tvRpmEstado.text = "Analizando..."
            tvRpmEstado.setTextColor(Color.parseColor("#38BDF8"))
            tvRpmValues.text = "-- RPM"
            return
        }

        val intervals = mutableListOf<Long>()
        for (i in 1 until peakTimestamps.size) {
            intervals.add(peakTimestamps[i] - peakTimestamps[i - 1])
        }

        val avgIntervalMs = intervals.average()
        val rpm = (60000.0 / avgIntervalMs).roundToInt()

        tvRpmValues.text = String.format(Locale.getDefault(), "%d RPM", rpm)

        val (statusText, colorHex) = when {
            rpm < 10 -> Pair("Bradipnea (Respiración Lenta)", "#38BDF8")
            rpm in 10..20 -> Pair("Eupnea (Respiración Normal)", "#00E676")
            else -> Pair("Taquipnea (Respiración Acelerada)", "#FF5252")
        }

        tvRpmEstado.text = statusText
        tvRpmEstado.setTextColor(Color.parseColor(colorHex))
        tvRpmValues.setTextColor(Color.parseColor(colorHex))
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {
        // No usado
    }

    private data class Quad<A, B, C, D>(val first: A, val second: B, val third: C, val fourth: D)
}