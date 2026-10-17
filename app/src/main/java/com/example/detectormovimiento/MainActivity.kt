package com.example.detectormovimiento

import android.content.ClipData
import android.content.ClipboardManager
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
import android.text.SpannableString
import android.text.Spanned
import android.text.style.RelativeSizeSpan
import android.view.View
import android.widget.Button
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import com.example.detectormovimiento.AnalizadorVital.EstadoMedicion
import com.example.detectormovimiento.DetectorSismico.Fase
import com.example.detectormovimiento.DetectorSismico.Nivel
import com.example.detectormovimiento.HistorialRepositorio.RegistroMedicion
import com.example.detectormovimiento.HistorialRepositorio.RegistroSismo
import com.example.detectormovimiento.HistorialRepositorio.TipoMedicion
import com.google.android.material.bottomnavigation.BottomNavigationView
import com.google.android.material.button.MaterialButtonToggleGroup
import com.google.android.material.progressindicator.LinearProgressIndicator
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.abs
import kotlin.math.sqrt

class MainActivity : AppCompatActivity(), SensorEventListener {

    private lateinit var sensorManager: SensorManager
    private var linearAccelSensor: Sensor? = null
    private var accelSensor: Sensor? = null
    private var isLinearAvailable = false

    // Navegación
    private lateinit var navInferior: BottomNavigationView
    private lateinit var scrollSismos: View
    private lateinit var scrollSignos: View
    private lateinit var layoutHistorial: View
    private lateinit var tvEstadoGlobal: TextView
    private lateinit var ivEstadoGlobal: ImageView
    private lateinit var chipEstado: View

    // Seismic UI Views
    private lateinit var circuloEstado: View
    private lateinit var ivEstadoSismo: ImageView
    private lateinit var tvStatusBadge: TextView
    private lateinit var tvNivelMovimiento: TextView
    private lateinit var tvAceleracionPico: TextView
    private lateinit var tvQuake30sTimer: TextView
    private lateinit var tvMercalliDetalle: TextView
    private lateinit var tvMagnitud: TextView
    private lateinit var tvCategoriaRichter: TextView
    private lateinit var tvDetalleRichter: TextView
    private lateinit var listaRichter: LinearLayout
    private lateinit var btnRestablecer: Button
    private lateinit var sismografoView: SismografoView

    // Biometric UI Views
    private lateinit var grupoPanel: MaterialButtonToggleGroup
    private lateinit var grupoSexo: MaterialButtonToggleGroup
    private lateinit var btnPanelRespiracion: Button
    private lateinit var btnPanelPulso: Button
    private lateinit var layoutPanelRespiracion: View
    private lateinit var layoutPanelPulso: View
    private lateinit var btnHombre: Button
    private lateinit var btnMujer: Button
    private lateinit var imgRespiracion: ImageView
    private lateinit var tvInstruccionRespiracion: TextView
    private lateinit var btnMedirRespiracion: Button
    private lateinit var btnMedirPulso: Button
    private lateinit var btnCancelarBiometric: Button
    private lateinit var pbBiometricProgress: LinearProgressIndicator
    private lateinit var ivEstadoMedicion: ImageView
    private lateinit var tvBiometricStatus: TextView
    private lateinit var tvRpmEstado: TextView
    private lateinit var tvRpmValues: TextView
    private lateinit var tvBpmEstado: TextView
    private lateinit var tvBpmValues: TextView

    // Historial UI Views
    private lateinit var grupoHistorial: MaterialButtonToggleGroup
    private lateinit var tvHistorialResumen: TextView
    private lateinit var layoutHistorialVacio: View
    private lateinit var tvHistorialVacio: TextView
    private lateinit var listaHistorial: LinearLayout

    private lateinit var historial: HistorialRepositorio
    private var historialMostrandoSismos = true
    private val formatoFecha = SimpleDateFormat("dd/MM/yy\nHH:mm", Locale.getDefault())

    private var toneGenerator: ToneGenerator? = null
    private val handler = Handler(Looper.getMainLooper())

    // Sismología
    private val detector = DetectorSismico()
    private var ultimaFase: Fase? = null
    private var faseAnteriorMuestra: Fase? = null
    private var ultimoNivel = Nivel.NINGUNO
    private var ultimaActualizacionUi = 0L
    private var alarmaSonando = false
    private var sismoActual: RegistroSismo? = null
    private var ultimoGuardadoSismoMs = 0L
    private var ultimoAspectoEstado: Pair<Fase, Nivel>? = null

    // Biometría: respiración y pulso se miden por separado
    private var panelSignos = TipoMedicion.RESPIRACION
    private var tipoActual = TipoMedicion.RESPIRACION
    private var esMujer = false
    private var isBiometricMeasuring = false
    private var duracionCapturaMs = DURACION_RESPIRACION_MS
    private var biometricStartTime = 0L
    private val tiemposNs = ArrayList<Long>()
    private val muestrasX = ArrayList<Float>()
    private val muestrasY = ArrayList<Float>()
    private val muestrasZ = ArrayList<Float>()
    private var biometricProgressRunnable: Runnable? = null
    private var reintentoPulsoRunnable: Runnable? = null
    private var medicionActiva = false
    private var medicionId = 0L
    private var intentoPulso = 0

    /** Cómo se ve el mensaje de estado de una medición: color e ícono. */
    private enum class Tono(val color: String, val icono: Int) {
        NEUTRO("#CBD5E1", R.drawable.ic_info),
        PROGRESO("#38BDF8", R.drawable.ic_reloj),
        REINTENTO("#FBBF24", R.drawable.ic_reintentar),
        OK("#22C55E", R.drawable.ic_check_circulo),
        AVISO("#FBBF24", R.drawable.ic_alerta),
        ERROR("#F87171", R.drawable.ic_error),
    }

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

        historial = HistorialRepositorio(filesDir)

        // Navegación
        navInferior = findViewById(R.id.navInferior)
        scrollSismos = findViewById(R.id.scrollSismos)
        scrollSignos = findViewById(R.id.scrollSignos)
        layoutHistorial = findViewById(R.id.layoutHistorial)
        tvEstadoGlobal = findViewById(R.id.tvEstadoGlobal)
        ivEstadoGlobal = findViewById(R.id.ivEstadoGlobal)
        chipEstado = findViewById(R.id.chipEstado)

        // Initialize Seismic Views
        circuloEstado = findViewById(R.id.circuloEstado)
        ivEstadoSismo = findViewById(R.id.ivEstadoSismo)
        tvStatusBadge = findViewById(R.id.tvStatusBadge)
        tvNivelMovimiento = findViewById(R.id.tvNivelMovimiento)
        tvAceleracionPico = findViewById(R.id.tvAceleracionPico)
        tvQuake30sTimer = findViewById(R.id.tvQuake30sTimer)
        tvMercalliDetalle = findViewById(R.id.tvMercalliDetalle)
        tvMagnitud = findViewById(R.id.tvMagnitud)
        tvCategoriaRichter = findViewById(R.id.tvCategoriaRichter)
        tvDetalleRichter = findViewById(R.id.tvDetalleRichter)
        listaRichter = findViewById(R.id.listaRichter)
        btnRestablecer = findViewById(R.id.btnRestablecer)
        sismografoView = findViewById(R.id.sismografoView)

        // Initialize Biometric Views
        grupoPanel = findViewById(R.id.grupoPanel)
        grupoSexo = findViewById(R.id.grupoSexo)
        btnPanelRespiracion = findViewById(R.id.btnPanelRespiracion)
        btnPanelPulso = findViewById(R.id.btnPanelPulso)
        layoutPanelRespiracion = findViewById(R.id.layoutPanelRespiracion)
        layoutPanelPulso = findViewById(R.id.layoutPanelPulso)
        btnHombre = findViewById(R.id.btnHombre)
        btnMujer = findViewById(R.id.btnMujer)
        imgRespiracion = findViewById(R.id.imgRespiracion)
        tvInstruccionRespiracion = findViewById(R.id.tvInstruccionRespiracion)
        btnMedirRespiracion = findViewById(R.id.btnMedirRespiracion)
        btnMedirPulso = findViewById(R.id.btnMedirPulso)
        btnCancelarBiometric = findViewById(R.id.btnCancelarBiometric)
        pbBiometricProgress = findViewById(R.id.pbBiometricProgress)
        ivEstadoMedicion = findViewById(R.id.ivEstadoMedicion)
        tvBiometricStatus = findViewById(R.id.tvBiometricStatus)
        tvRpmEstado = findViewById(R.id.tvRpmEstado)
        tvRpmValues = findViewById(R.id.tvRpmValues)
        tvBpmEstado = findViewById(R.id.tvBpmEstado)
        tvBpmValues = findViewById(R.id.tvBpmValues)

        // Initialize History Views
        grupoHistorial = findViewById(R.id.grupoHistorial)
        tvHistorialResumen = findViewById(R.id.tvHistorialResumen)
        layoutHistorialVacio = findViewById(R.id.layoutHistorialVacio)
        tvHistorialVacio = findViewById(R.id.tvHistorialVacio)
        listaHistorial = findViewById(R.id.listaHistorial)

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
            tvStatusBadge.text = "ESTE TELÉFONO NO TIENE ACELERÓMETRO"
            tvStatusBadge.setTextColor(Color.parseColor("#F87171"))
        }

        navInferior.setOnItemSelectedListener { item ->
            mostrarPestana(item.itemId)
            true
        }
        chipEstado.setOnClickListener { navInferior.selectedItemId = R.id.nav_sismos }

        btnRestablecer.setOnClickListener {
            detenerAlarma()
            finalizarSismo()
            detector.restablecer()
            ultimaFase = null
        }

        grupoPanel.addOnButtonCheckedListener { _, id, marcado ->
            if (marcado) mostrarPanel(if (id == R.id.btnPanelPulso) TipoMedicion.PULSO else TipoMedicion.RESPIRACION)
        }
        grupoSexo.addOnButtonCheckedListener { _, id, marcado ->
            if (marcado) seleccionarSexo(id == R.id.btnMujer)
        }
        btnMedirRespiracion.setOnClickListener { iniciarMedicion(TipoMedicion.RESPIRACION) }
        btnMedirPulso.setOnClickListener { iniciarMedicion(TipoMedicion.PULSO) }
        btnCancelarBiometric.setOnClickListener { cancelarMedicion("Medición cancelada.") }
        mostrarPanel(TipoMedicion.RESPIRACION)
        val mujerGuardado = getPreferences(MODE_PRIVATE).getBoolean(PREF_MUJER, false)
        grupoSexo.check(if (mujerGuardado) R.id.btnMujer else R.id.btnHombre)
        seleccionarSexo(mujerGuardado)
        actualizarBotonesMedicion()

        grupoHistorial.addOnButtonCheckedListener { _, id, marcado ->
            if (marcado) {
                historialMostrandoSismos = id == R.id.btnHistSismos
                mostrarHistorial()
            }
        }
        findViewById<Button>(R.id.btnBorrarHistorial).setOnClickListener { confirmarBorrarHistorial() }

        mostrarEscalaRichter(null)
        mostrarErrorAnteriorSiHay()
    }

    // ===================== NAVEGACIÓN =====================

    private fun mostrarPestana(itemId: Int) {
        scrollSismos.visibility = if (itemId == R.id.nav_sismos) View.VISIBLE else View.GONE
        scrollSignos.visibility = if (itemId == R.id.nav_signos) View.VISIBLE else View.GONE
        layoutHistorial.visibility = if (itemId == R.id.nav_historial) View.VISIBLE else View.GONE
        if (itemId == R.id.nav_historial) mostrarHistorial()
    }

    /** El mismo color con transparencia (para fondos suaves detrás de un ícono). */
    private fun conAlfa(color: Int, alfa: Int) = (color and 0x00FFFFFF) or (alfa shl 24)

    private fun ponerEstadoGlobal(icono: Int, texto: String, color: String) {
        val c = Color.parseColor(color)
        tvEstadoGlobal.text = texto
        tvEstadoGlobal.setTextColor(c)
        ivEstadoGlobal.setImageResource(icono)
        ivEstadoGlobal.imageTintList = ColorStateList.valueOf(c)
        chipEstado.backgroundTintList = ColorStateList.valueOf(conAlfa(c, 0x26))
    }

    /** Si la app se cerró por un error la vez anterior, lo muestra para poder reportarlo. */
    private fun mostrarErrorAnteriorSiHay() {
        val archivo = EhriaApp.archivoError(application)
        if (!archivo.exists()) return
        val detalle = try {
            archivo.readText()
        } catch (_: Exception) {
            ""
        }
        archivo.delete()
        if (detalle.isBlank()) return
        AlertDialog.Builder(this)
            .setTitle("La app se cerró la última vez")
            .setMessage("Copia este detalle y compártelo con el equipo:\n\n" + detalle.take(3000))
            .setPositiveButton("Copiar") { _, _ ->
                val portapapeles = getSystemService(CLIPBOARD_SERVICE) as ClipboardManager
                portapapeles.setPrimaryClip(ClipData.newPlainText("Error EHRIA", detalle))
                Toast.makeText(this, "Detalle copiado", Toast.LENGTH_SHORT).show()
            }
            .setNegativeButton("Cerrar", null)
            .show()
    }

    // ===================== SISMOLOGÍA =====================

    private fun processSeismicSensor(event: SensorEvent) {
        // Durante una medición el teléfono está sobre una persona: no se evalúan sismos.
        if (medicionActiva) return
        val x = event.values[0]
        val y = event.values[1]
        val z = event.values[2]
        val total = sqrt(x * x + y * y + z * z)
        // Sin sensor de aceleración lineal, se resta la gravedad a la magnitud.
        val magnitud = if (isLinearAvailable) total else abs(total - SensorManager.GRAVITY_EARTH)

        val ahora = SystemClock.elapsedRealtime()
        val estado = detector.procesar(magnitud, ahora)

        sismografoView.agregarPunto(estado.pga)
        registrarSismo(estado)

        if (estado.alertaNueva) {
            ultimoNivel = estado.nivel
            iniciarAlarma()
            // Ante un sismo, mostrar la pestaña de sismos esté donde esté el usuario.
            if (navInferior.selectedItemId != R.id.nav_sismos) navInferior.selectedItemId = R.id.nav_sismos
        }

        // La interfaz se actualiza 10 veces por segundo (o al cambiar de fase) para no
        // trabar los teléfonos viejos; el sensor llega a unas 50 muestras por segundo.
        if (estado.fase == ultimaFase && !estado.alertaNueva && ahora - ultimaActualizacionUi < 100L) return
        ultimaActualizacionUi = ahora
        if (estado.fase != ultimaFase && estado.fase != Fase.ALERTA) detenerAlarma()
        ultimaFase = estado.fase
        mostrarEstadoSismico(estado)
    }

    /** Guarda el sismo en el historial al empezar, al subir de nivel y al terminar. */
    private fun registrarSismo(estado: DetectorSismico.Estado) {
        val salioDeAlerta = faseAnteriorMuestra == Fase.ALERTA && estado.fase != Fase.ALERTA
        faseAnteriorMuestra = estado.fase

        if (estado.fase == Fase.ALERTA) {
            val actual = sismoActual
            val registro = RegistroSismo(
                id = actual?.id ?: System.currentTimeMillis(),
                fechaMs = actual?.fechaMs ?: (System.currentTimeMillis() - estado.segundosEvaluados * 1000L),
                nivel = estado.nivel,
                pgaPico = estado.pgaPico,
                duracionS = estado.duracionMovimientoS,
                pgaPicoBruto = estado.pgaPicoBruto,
            )
            sismoActual = registro
            // Guardar al empezar, al subir de nivel y cada 5 s (por si la app se cierra de golpe).
            val ahora = SystemClock.elapsedRealtime()
            if (estado.alertaNueva || ahora - ultimoGuardadoSismoMs >= 5_000L) {
                ultimoGuardadoSismoMs = ahora
                guardarSismo(registro)
            }
        } else if (salioDeAlerta) {
            finalizarSismo()
        }
    }

    private fun finalizarSismo() {
        sismoActual?.let { guardarSismo(it) }
        sismoActual = null
    }

    private fun guardarSismo(registro: RegistroSismo) {
        historial.guardarSismo(registro)
        if (layoutHistorial.visibility == View.VISIBLE) mostrarHistorial()
    }

    private fun mostrarEstadoSismico(estado: DetectorSismico.Estado) {
        val color: String
        val textoGlobal: String
        val icono: Int
        when (estado.fase) {
            Fase.CALIBRANDO -> {
                color = "#38BDF8"
                textoGlobal = "Calibrando"
                icono = R.drawable.ic_calibrar
                tvStatusBadge.text = "CALIBRANDO SENSOR"
                tvNivelMovimiento.text = "Deja el teléfono quieto unos segundos"
                tvQuake30sTimer.text = "Midiendo el ruido propio del sensor de este teléfono."
                tvMercalliDetalle.text = "Intensidad: I - Imperceptible. Sistema iniciando."
                btnRestablecer.visibility = View.GONE
            }

            Fase.NORMAL -> {
                color = "#22C55E"
                textoGlobal = "Vigilando"
                icono = R.drawable.ic_check_circulo
                tvStatusBadge.text = "SISTEMA NORMAL"
                tvNivelMovimiento.text = "Sin Actividad Sísmica Sostenida"
                tvQuake30sTimer.text = String.format(
                    Locale.getDefault(),
                    "Umbral de este teléfono: %.2f m/s²", estado.umbral
                )
                tvMercalliDetalle.text = "Intensidad: I - Imperceptible. Sistema monitoreando."
                btnRestablecer.visibility = View.GONE
            }

            Fase.EVALUANDO -> {
                color = "#FBBF24"
                val segundos = estado.segundosEvaluados.coerceAtMost(10)
                textoGlobal = "Evaluando $segundos/10 s"
                icono = R.drawable.ic_reloj
                tvStatusBadge.text = String.format(
                    Locale.getDefault(), "EVALUANDO MOVIMIENTO · %d / 10 s", segundos
                )
                tvNivelMovimiento.text = "Movimiento en verificación..."
                tvQuake30sTimer.text = String.format(
                    Locale.getDefault(),
                    "Verificando si el movimiento persiste 10 s (%d s transcurridos)", segundos
                )
                btnRestablecer.visibility = View.GONE
            }

            Fase.ALERTA -> {
                val magnitud = EscalaSismica.magnitudEstimada(estado.pgaPicoBruto)
                val mercalli = EscalaSismica.intensidadMercalli(estado.pgaPicoBruto)
                val instruccion = when (estado.nivel) {
                    Nivel.FUERTE -> "Agáchate, cúbrete y sujétate. Al terminar, evacua."
                    Nivel.MODERADO -> "Agáchate, cúbrete y sujétate."
                    else -> "Mantén la calma y aléjate de ventanas."
                }
                val textos = Triple(
                    when (estado.nivel) {
                        Nivel.FUERTE -> "ALERTA MÁXIMA · SACUDIDA FUERTE"
                        Nivel.MODERADO -> "SACUDIDA MODERADA DETECTADA"
                        else -> "SACUDIDA LEVE DETECTADA"
                    },
                    if (magnitud != null) {
                        String.format(
                            Locale.getDefault(), "Magnitud ≈ %.1f · %s",
                            magnitud, EscalaSismica.categoriaRichter(magnitud).nombre
                        )
                    } else "Sismo en curso",
                    "Intensidad ${EscalaSismica.romano(mercalli)} (${EscalaSismica.descripcionMercalli(mercalli)}) en este lugar. $instruccion"
                )
                mostrarEscalaRichter(magnitud)
                color = colorNivel(estado.nivel)
                textoGlobal = "Sacudida ${nombreNivel(estado.nivel).lowercase(Locale.getDefault())}"
                icono = R.drawable.ic_alerta
                tvStatusBadge.text = textos.first
                tvNivelMovimiento.text = textos.second
                tvMercalliDetalle.text = textos.third
                tvQuake30sTimer.text = String.format(
                    Locale.getDefault(),
                    "Sismo verificado: %d s de movimiento · pico %.2f m/s²",
                    estado.duracionMovimientoS, estado.pgaPicoBruto
                )
                btnRestablecer.visibility = View.VISIBLE
            }
        }

        val c = Color.parseColor(color)
        tvAceleracionPico.text = String.format(Locale.getDefault(), "Aceleración actual: %.2f m/s²", estado.pga)
        tvEstadoGlobal.text = textoGlobal
        // Íconos y colores solo cambian con la fase o el nivel (no 10 veces por segundo).
        val aspecto = Pair(estado.fase, estado.nivel)
        if (aspecto == ultimoAspectoEstado) return
        ultimoAspectoEstado = aspecto
        sismografoView.setStrokeColor(color)
        tvStatusBadge.setTextColor(c)
        tvAceleracionPico.setTextColor(c)
        ivEstadoSismo.setImageResource(icono)
        ivEstadoSismo.imageTintList = ColorStateList.valueOf(c)
        circuloEstado.backgroundTintList = ColorStateList.valueOf(conAlfa(c, 0x26))
        ponerEstadoGlobal(icono, textoGlobal, color)
    }

    /** Llena la tarjeta de Richter y resalta la categoría de [magnitud] (null = sin sismo). */
    private fun mostrarEscalaRichter(magnitud: Double?) {
        val actual = magnitud?.let { EscalaSismica.categoriaRichter(it) }
        val color = Color.parseColor(if (actual != null) colorRichter(magnitud) else "#94A3B8")
        if (magnitud != null && actual != null) {
            tvMagnitud.text = String.format(Locale.getDefault(), "%.1f", magnitud)
            tvMagnitud.setTextColor(color)
            tvCategoriaRichter.text = actual.nombre
            tvDetalleRichter.text = "Magnitud estimada del último sismo"
        }
        listaRichter.removeAllViews()
        for (categoria in EscalaSismica.CATEGORIAS_RICHTER) {
            val fila = layoutInflater.inflate(R.layout.item_richter, listaRichter, false)
            val nombre = fila.findViewById<TextView>(R.id.tvRichterNombre)
            val rango = fila.findViewById<TextView>(R.id.tvRichterRango)
            nombre.text = categoria.nombre
            rango.text = categoria.hasta?.let {
                String.format(Locale.getDefault(), "%.1f – %.1f", categoria.desde, it - 0.1)
            } ?: String.format(Locale.getDefault(), "%.1f o más", categoria.desde)
            if (categoria == actual) {
                fila.backgroundTintList = ColorStateList.valueOf(conAlfa(color, 0x30))
                nombre.setTextColor(color)
                rango.setTextColor(color)
                nombre.setTypeface(nombre.typeface, android.graphics.Typeface.BOLD)
                rango.setTypeface(rango.typeface, android.graphics.Typeface.BOLD)
            }
            listaRichter.addView(fila)
        }
    }

    private fun colorRichter(magnitud: Double) = when {
        magnitud >= 6.0 -> "#F87171"
        magnitud >= 4.0 -> "#FBBF24"
        else -> "#38BDF8"
    }

    private fun iniciarAlarma() {
        handler.removeCallbacks(alarmaRunnable)
        alarmaSonando = true
        handler.post(alarmaRunnable)
    }

    private fun detenerAlarma() {
        alarmaSonando = false
        handler.removeCallbacks(alarmaRunnable)
        try {
            toneGenerator?.stopTone()
        } catch (_: Exception) {
            // Ignorar
        }
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

    private fun mostrarPanel(tipo: TipoMedicion) {
        panelSignos = tipo
        val respiracion = tipo == TipoMedicion.RESPIRACION
        layoutPanelRespiracion.visibility = if (respiracion) View.VISIBLE else View.GONE
        layoutPanelPulso.visibility = if (respiracion) View.GONE else View.VISIBLE
        pbBiometricProgress.setIndicatorColor(Color.parseColor(if (respiracion) "#38BDF8" else "#F43F5E"))
    }

    /** Hombres: abdomen. Mujeres: pecho. (El pulso siempre va en el lado izquierdo del pecho.) */
    private fun seleccionarSexo(mujer: Boolean) {
        esMujer = mujer
        getPreferences(MODE_PRIVATE).edit().putBoolean(PREF_MUJER, mujer).apply()
        imgRespiracion.setImageResource(
            if (mujer) R.drawable.ilustracion_respiracion_mujer else R.drawable.ilustracion_respiracion_hombre
        )
        tvInstruccionRespiracion.text = if (mujer) {
            "1. Recuéstate boca arriba.\n2. Pon el teléfono plano, con la pantalla hacia arriba, sobre el CENTRO DEL PECHO (en las mujeres la respiración se nota más en el pecho).\n3. Quédate quieta y respira normal hasta que termine."
        } else {
            "1. Recuéstate boca arriba.\n2. Pon el teléfono plano, con la pantalla hacia arriba, sobre el ABDOMEN, a la altura del ombligo (en los hombres la respiración se nota más en el abdomen).\n3. Quédate quieto y respira normal hasta que termine."
        }
    }

    private fun iniciarMedicion(tipo: TipoMedicion) {
        if (medicionActiva) return
        medicionActiva = true
        tipoActual = tipo
        medicionId = System.currentTimeMillis()
        intentoPulso = 0
        pausarSismos()
        actualizarBotonesMedicion()
        if (tipo == TipoMedicion.RESPIRACION) {
            mostrarValor(tvRpmValues, tvRpmEstado, "-- RPM", "Midiendo…", "#38BDF8")
            capturar(DURACION_RESPIRACION_MS)
        } else {
            mostrarValor(tvBpmValues, tvBpmEstado, "-- BPM", "Midiendo…", "#F43F5E")
            capturar(DURACION_PULSO_MS)
        }
    }

    private fun actualizarBotonesMedicion() {
        val libre = !medicionActiva && accelSensor != null
        btnMedirRespiracion.isEnabled = libre
        btnMedirPulso.isEnabled = libre
        btnPanelRespiracion.isEnabled = !medicionActiva
        btnPanelPulso.isEnabled = !medicionActiva
        btnHombre.isEnabled = !medicionActiva
        btnMujer.isEnabled = !medicionActiva
        btnCancelarBiometric.visibility = if (medicionActiva) View.VISIBLE else View.GONE
    }

    /** Mientras se mide, el teléfono está sobre una persona: sus movimientos no son sismos. */
    private fun pausarSismos() {
        detenerAlarma()
        finalizarSismo()
        ultimaFase = null
        ultimoAspectoEstado = null
        ponerEstadoGlobal(R.drawable.ic_pausa, "Sismos en pausa", "#94A3B8")
    }

    private fun reanudarSismos() {
        detector.restablecer()
        ultimaFase = null
        ultimoAspectoEstado = null
    }

    private fun capturar(duracionMs: Long) {
        val sensor = accelSensor
        if (sensor == null) {
            cancelarMedicion("Este teléfono no tiene acelerómetro.")
            return
        }

        duracionCapturaMs = duracionMs
        isBiometricMeasuring = true
        biometricStartTime = SystemClock.elapsedRealtime()
        tiemposNs.clear()
        muestrasX.clear()
        muestrasY.clear()
        muestrasZ.clear()

        // Más muestras por segundo para poder ver la vibración de cada latido.
        sensorManager.unregisterListener(this, sensor)
        sensorManager.registerListener(this, sensor, PERIODO_BIOMETRIA_US)

        pbBiometricProgress.visibility = View.VISIBLE
        pbBiometricProgress.progress = 0
        when {
            tipoActual == TipoMedicion.RESPIRACION -> ponerEstadoMedicion("Midiendo la respiración… quédate quieto", Tono.PROGRESO)
            intentoPulso == 0 -> ponerEstadoMedicion("Midiendo el pulso… no hables ni te muevas", Tono.PROGRESO)
            else -> ponerEstadoMedicion("Buscando el pulso · intento ${intentoPulso + 1}", Tono.REINTENTO)
        }

        biometricProgressRunnable?.let { handler.removeCallbacks(it) }
        biometricProgressRunnable = object : Runnable {
            override fun run() {
                if (!isBiometricMeasuring) return
                val elapsed = SystemClock.elapsedRealtime() - biometricStartTime
                pbBiometricProgress.progress = ((elapsed * 100) / duracionCapturaMs).toInt().coerceIn(0, 100)

                if (elapsed >= duracionCapturaMs) {
                    alTerminarCaptura()
                } else {
                    handler.postDelayed(this, 100)
                }
            }
        }
        handler.post(biometricProgressRunnable!!)
    }

    private fun detenerCaptura() {
        isBiometricMeasuring = false
        biometricProgressRunnable?.let { handler.removeCallbacks(it) }
        pbBiometricProgress.visibility = View.GONE
        // Volver a la frecuencia normal para ahorrar batería.
        accelSensor?.let {
            sensorManager.unregisterListener(this, it)
            sensorManager.registerListener(this, it, SensorManager.SENSOR_DELAY_GAME)
        }
    }

    private fun terminarMedicion() {
        medicionActiva = false
        reintentoPulsoRunnable?.let { handler.removeCallbacks(it) }
        reintentoPulsoRunnable = null
        actualizarBotonesMedicion()
        reanudarSismos()
    }

    private fun alTerminarCaptura() {
        detenerCaptura()

        val resultado = AnalizadorVital.analizar(
            tiemposNs.toLongArray(),
            muestrasX.toFloatArray(),
            muestrasY.toFloatArray(),
            muestrasZ.toFloatArray(),
        )

        if (tipoActual == TipoMedicion.RESPIRACION) {
            alTerminarRespiracion(resultado)
        } else {
            alTerminarPulso(resultado)
        }
    }

    private fun alTerminarRespiracion(resultado: AnalizadorVital.Resultado) {
        val rojo = "#F87171"
        val posicion = if (esMujer) "pecho (mujer)" else "abdomen (hombre)"
        when (resultado.estado) {
            EstadoMedicion.MUESTRA_INSUFICIENTE -> {
                mostrarValor(tvRpmValues, tvRpmEstado, "-- RPM", "Sin medición", rojo)
                mostrarFinal("Muestra insuficiente. Intenta de nuevo.", Tono.ERROR)
                guardarMedicion(null, null, "Muestra insuficiente")
            }

            EstadoMedicion.SIN_MOVIMIENTO -> {
                mostrarValor(tvRpmValues, tvRpmEstado, "0 RPM", "Sin movimiento", rojo)
                mostrarFinal("No se detectó la respiración. ¿Está el teléfono sobre una mesa?", Tono.ERROR)
                guardarMedicion(null, null, "Sin movimiento · $posicion")
            }

            EstadoMedicion.MUCHO_MOVIMIENTO -> {
                mostrarValor(tvRpmValues, tvRpmEstado, "-- RPM", "Repetir", rojo)
                mostrarFinal("Hubo demasiado movimiento. Quédate quieto y repite.", Tono.AVISO)
                guardarMedicion(null, null, "Demasiado movimiento · $posicion")
            }

            EstadoMedicion.RESPIRACION_IRREGULAR, EstadoMedicion.OK -> {
                val rpm = resultado.rpm
                if (rpm == null) {
                    mostrarValor(tvRpmValues, tvRpmEstado, "-- RPM", "Irregular, no se pudo medir", "#FBBF24")
                    mostrarFinal("La respiración fue irregular. Intenta de nuevo, respirando normal.", Tono.AVISO)
                    guardarMedicion(null, null, "Respiración irregular · $posicion")
                } else {
                    val estado = mostrarRpm(rpm)
                    mostrarFinal("Respiración medida · valor orientativo", Tono.OK)
                    guardarMedicion(rpm, null, "$estado · $posicion")
                }
            }
        }
    }

    private fun alTerminarPulso(resultado: AnalizadorVital.Resultado) {
        val rojo = "#F87171"
        val bpm = resultado.bpm
        when {
            bpm != null -> {
                val estado = mostrarBpm(bpm)
                val extra = if (intentoPulso > 0) " (en el intento ${intentoPulso + 1})" else ""
                mostrarFinal("Pulso medido$extra · valor orientativo", Tono.OK)
                guardarMedicion(null, bpm, estado)
            }

            resultado.estado == EstadoMedicion.MUESTRA_INSUFICIENTE -> {
                mostrarValor(tvBpmValues, tvBpmEstado, "-- BPM", "Sin medición", rojo)
                mostrarFinal("Muestra insuficiente. Intenta de nuevo.", Tono.ERROR)
                guardarMedicion(null, null, "Muestra insuficiente")
            }

            resultado.estado == EstadoMedicion.SIN_MOVIMIENTO -> {
                // No hay respiración: el teléfono no está sobre una persona. Reintentar no sirve.
                mostrarValor(tvBpmValues, tvBpmEstado, "-- BPM", "Teléfono fuera del pecho", rojo)
                mostrarFinal("El teléfono no parece estar sobre el pecho. Colócalo y vuelve a medir.", Tono.ERROR)
                guardarMedicion(null, null, "Pulso no detectado (teléfono fuera del pecho)")
            }

            resultado.frecuenciaMuestreoHz < FRECUENCIA_MIN_PULSO_HZ -> {
                mostrarValor(tvBpmValues, tvBpmEstado, "-- BPM", "Sensor muy lento", "#FBBF24")
                mostrarFinal("El sensor de este teléfono es demasiado lento para medir el pulso.", Tono.AVISO)
                guardarMedicion(null, null, "Sensor muy lento para el pulso")
            }

            // El teléfono está sobre una persona (se nota la respiración) pero el pulso no
            // salió claro: volver a medirlo automáticamente hasta encontrarlo.
            else -> {
                val motivo = if (resultado.estado == EstadoMedicion.MUCHO_MOVIMIENTO) "Hubo movimiento" else "Señal débil"
                mostrarValor(tvBpmValues, tvBpmEstado, "-- BPM", "$motivo, reintentando…", "#FBBF24")
                programarReintentoPulso()
            }
        }
    }

    private fun programarReintentoPulso() {
        intentoPulso++
        ponerEstadoMedicion("No se detectó el pulso. Reintentando…", Tono.REINTENTO)
        val reintento = Runnable {
            reintentoPulsoRunnable = null
            if (medicionActiva) capturar(DURACION_PULSO_MS)
        }
        reintentoPulsoRunnable = reintento
        handler.postDelayed(reintento, 800L)
    }

    private fun cancelarMedicion(mensaje: String) {
        val estabaMidiendo = medicionActiva
        if (isBiometricMeasuring) detenerCaptura()
        if (estabaMidiendo) terminarMedicion()
        ponerEstadoMedicion(mensaje, Tono.NEUTRO)
        if (estabaMidiendo) {
            if (tipoActual == TipoMedicion.RESPIRACION) {
                mostrarValor(tvRpmValues, tvRpmEstado, "-- RPM", "Cancelado", "#94A3B8")
            } else {
                mostrarValor(tvBpmValues, tvBpmEstado, "-- BPM", "Cancelado", "#94A3B8")
                if (intentoPulso > 0) guardarMedicion(null, null, "Pulso cancelado")
            }
        }
    }

    private fun mostrarFinal(mensaje: String, tono: Tono) {
        terminarMedicion()
        ponerEstadoMedicion(mensaje, tono)
    }

    private fun ponerEstadoMedicion(mensaje: String, tono: Tono) {
        val c = Color.parseColor(tono.color)
        tvBiometricStatus.text = mensaje
        tvBiometricStatus.setTextColor(c)
        ivEstadoMedicion.setImageResource(tono.icono)
        ivEstadoMedicion.imageTintList = ColorStateList.valueOf(c)
    }

    /** Muestra el valor y devuelve su clasificación. */
    private fun mostrarRpm(rpm: Int): String {
        val (rpmStatus, rpmColor) = when {
            rpm < 12 -> Pair("Bradipnea (Lenta)", "#38BDF8")
            rpm <= 20 -> Pair("Eupnea (Normal)", "#22C55E")
            else -> Pair("Taquipnea (Acelerada)", "#F87171")
        }
        mostrarValor(tvRpmValues, tvRpmEstado, String.format(Locale.getDefault(), "%d RPM", rpm), rpmStatus, rpmColor)
        return rpmStatus
    }

    /** Muestra el valor y devuelve su clasificación. */
    private fun mostrarBpm(bpm: Int): String {
        val (bpmStatus, bpmColor) = when {
            bpm < 60 -> Pair("Bradicardia (Lento)", "#38BDF8")
            bpm <= 100 -> Pair("Pulso Normal", "#22C55E")
            else -> Pair("Taquicardia (Acelerado)", "#F87171")
        }
        mostrarValor(tvBpmValues, tvBpmEstado, String.format(Locale.getDefault(), "%d BPM", bpm), bpmStatus, bpmColor)
        return bpmStatus
    }

    /** Muestra "15 RPM" con el número grande y la unidad más pequeña. */
    private fun mostrarValor(valor: TextView, estado: TextView, texto: String, textoEstado: String, color: String) {
        val c = Color.parseColor(color)
        val conUnidad = SpannableString(texto)
        val corte = texto.lastIndexOf(' ')
        if (corte > 0) conUnidad.setSpan(RelativeSizeSpan(0.4f), corte, texto.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
        valor.text = conUnidad
        valor.setTextColor(c)
        estado.text = textoEstado
        estado.setTextColor(c)
    }

    private fun guardarMedicion(rpm: Int?, bpm: Int?, resultado: String) {
        historial.guardarMedicion(
            RegistroMedicion(medicionId, medicionId, tipoActual, rpm, bpm, resultado, intentoPulso)
        )
    }

    // ===================== HISTORIAL =====================

    private fun mostrarHistorial() {
        listaHistorial.removeAllViews()

        if (historialMostrandoSismos) {
            val sismos = historial.sismos()
            tvHistorialResumen.text = when (sismos.size) {
                0 -> ""
                1 -> "1 sismo registrado en este teléfono"
                else -> "${sismos.size} sismos registrados en este teléfono"
            }
            tvHistorialVacio.text = "Todavía no se han detectado sismos.\nLos sismos confirmados aparecerán aquí."
            layoutHistorialVacio.visibility = if (sismos.isEmpty()) View.VISIBLE else View.GONE
            for (s in sismos) {
                val magnitud = EscalaSismica.magnitudEstimada(s.pgaPicoBruto)
                val mercalli = EscalaSismica.intensidadMercalli(s.pgaPicoBruto)
                agregarItemHistorial(
                    titulo = magnitud?.let {
                        String.format(
                            Locale.getDefault(), "Magnitud ≈ %.1f · %s", it, EscalaSismica.categoriaRichter(it).nombre
                        )
                    } ?: "Sacudida ${nombreNivel(s.nivel).lowercase(Locale.getDefault())}",
                    detalle = String.format(
                        Locale.getDefault(), "Intensidad %s · pico %.2f m/s² · %d s",
                        EscalaSismica.romano(mercalli), s.pgaPicoBruto, s.duracionS
                    ),
                    fechaMs = s.fechaMs,
                    color = colorNivel(s.nivel),
                    icono = R.drawable.ic_nav_sismo,
                )
            }
        } else {
            val mediciones = historial.mediciones()
            tvHistorialResumen.text = when (mediciones.size) {
                0 -> ""
                1 -> "1 medición guardada"
                else -> "${mediciones.size} mediciones guardadas"
            }
            tvHistorialVacio.text = "Todavía no hay mediciones.\nUsa la pestaña Signos vitales para medir."
            layoutHistorialVacio.visibility = if (mediciones.isEmpty()) View.VISIBLE else View.GONE
            for (m in mediciones) {
                val reintentos = if (m.intentosPulso > 0) " · ${m.intentosPulso} reintentos" else ""
                val esPulso = m.tipo == TipoMedicion.PULSO
                val valor = if (esPulso) m.bpm else m.rpm
                agregarItemHistorial(
                    titulo = if (esPulso) "Pulso: ${valor?.let { "$it BPM" } ?: "no detectado"}"
                    else "Respiración: ${valor?.let { "$it RPM" } ?: "no detectada"}",
                    detalle = m.resultado + reintentos,
                    fechaMs = m.fechaMs,
                    color = if (valor != null) (if (esPulso) "#F43F5E" else "#38BDF8") else "#94A3B8",
                    icono = if (esPulso) R.drawable.ic_corazon else R.drawable.ic_respiracion,
                )
            }
        }
    }

    private fun agregarItemHistorial(titulo: String, detalle: String, fechaMs: Long, color: String, icono: Int) {
        val item = layoutInflater.inflate(R.layout.item_historial, listaHistorial, false)
        val c = Color.parseColor(color)
        item.findViewById<View>(R.id.circuloItem).backgroundTintList = ColorStateList.valueOf(conAlfa(c, 0x26))
        item.findViewById<ImageView>(R.id.ivItemIcono).apply {
            setImageResource(icono)
            imageTintList = ColorStateList.valueOf(c)
        }
        item.findViewById<TextView>(R.id.tvItemTitulo).text = titulo
        item.findViewById<TextView>(R.id.tvItemDetalle).text = detalle
        item.findViewById<TextView>(R.id.tvItemFecha).text = formatoFecha.format(Date(fechaMs))
        listaHistorial.addView(item)
    }

    private fun confirmarBorrarHistorial() {
        AlertDialog.Builder(this)
            .setTitle("Borrar historial")
            .setMessage("Se borrarán todos los sismos y mediciones guardados en este teléfono.")
            .setPositiveButton("Borrar") { _, _ ->
                historial.borrarTodo()
                mostrarHistorial()
            }
            .setNegativeButton("Cancelar", null)
            .show()
    }

    private fun nombreNivel(nivel: Nivel) = when (nivel) {
        Nivel.FUERTE -> "Fuerte"
        Nivel.MODERADO -> "Moderada"
        Nivel.LEVE -> "Leve"
        Nivel.NINGUNO -> "Sin clasificar"
    }

    private fun colorNivel(nivel: Nivel) = when (nivel) {
        Nivel.FUERTE -> "#F87171"
        Nivel.MODERADO -> "#FBBF24"
        else -> "#38BDF8"
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
        if (medicionActiva) cancelarMedicion("Medición cancelada al salir de la app. Toca para empezar de nuevo.")
        sensorManager.unregisterListener(this)
        detenerAlarma()
        // Guardar lo que lleva el sismo en curso, sin cerrarlo: si la alerta sigue al volver,
        // se actualiza el mismo registro en vez de crear uno duplicado.
        sismoActual?.let { historial.guardarSismo(it) }
    }

    override fun onDestroy() {
        super.onDestroy()
        handler.removeCallbacksAndMessages(null)
        toneGenerator?.release()
        toneGenerator = null
    }

    override fun onSensorChanged(event: SensorEvent?) {
        if (event == null || event.values.size < 3) return

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
        const val DURACION_RESPIRACION_MS = 20_000L
        const val DURACION_PULSO_MS = 15_000L
        const val PREF_MUJER = "respiracion_mujer"
        const val FRECUENCIA_MIN_PULSO_HZ = 40.0
        const val PERIODO_BIOMETRIA_US = 5_000 // 200 Hz
    }
}
