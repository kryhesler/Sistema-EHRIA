package com.example.detectormovimiento

import android.content.ClipData
import android.content.BroadcastReceiver
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.ActivityInfo
import android.content.pm.ApplicationInfo
import android.content.res.ColorStateList
import android.graphics.Color
import android.graphics.Rect
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.hardware.usb.UsbManager
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import android.text.SpannableString
import android.text.Spanned
import android.text.style.RelativeSizeSpan
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.widget.Button
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import androidx.activity.OnBackPressedCallback
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import com.example.detectormovimiento.AnalizadorVital.EstadoMedicion
import com.example.detectormovimiento.DetectorSismico.Fase
import com.example.detectormovimiento.DetectorSismico.Nivel
import com.example.detectormovimiento.HistorialRepositorio.RegistroMedicion
import com.example.detectormovimiento.HistorialRepositorio.RegistroSismo
import com.example.detectormovimiento.HistorialRepositorio.TipoMedicion
import com.google.android.material.bottomnavigation.BottomNavigationView
import com.google.android.material.button.MaterialButton
import com.google.android.material.button.MaterialButtonToggleGroup
import com.google.android.material.materialswitch.MaterialSwitch
import com.google.android.material.progressindicator.LinearProgressIndicator
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import kotlin.math.abs
import kotlin.math.roundToInt
import kotlin.math.sqrt

class MainActivity : AppCompatActivity(), SensorEventListener {

    private lateinit var sensorManager: SensorManager
    private var linearAccelSensor: Sensor? = null
    private var accelSensor: Sensor? = null
    private var isLinearAvailable = false

    // Navegación
    private lateinit var navInferior: BottomNavigationView
    private lateinit var scrollSismos: View
    private lateinit var scrollSignos: ScrollView
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
    private lateinit var btnMedirRespiracion: MaterialButton
    private lateinit var btnMedirPulso: MaterialButton
    private lateinit var imgPulso: View
    private lateinit var tvInstruccionPulso: View
    private lateinit var cajaResultadoRespiracion: View
    private lateinit var cajaResultadoPulso: View
    private lateinit var layoutMonitor: View
    private lateinit var graficaVital: SismografoView
    private lateinit var tvCuenta: TextView
    private lateinit var ivCalidad: ImageView
    private lateinit var tvCalidad: TextView
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

    private val sonidos = Sonidos()
    private lateinit var btnSonido: ImageView
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
    private val filasRichter = mutableListOf<FilaRichter>()
    private var magnitudMostrada: Double? = null
    private var ultimoAspectoEstado: Pair<Fase, Nivel>? = null
    // Después de medir signos vitales, la persona levanta y acomoda el teléfono: esos
    // movimientos no son sismos. La detección se reanuda pasado este momento.
    private var reanudarSismosEnMs = 0L

    // Estación: Arduino (DHT, ultrasónico, servo, LED) y modo robot
    private enum class ModoRobot { NINGUNO, ESTACION, HERRAMIENTA }

    private var modoRobot = ModoRobot.NINGUNO
    private lateinit var arduino: ConexionArduino
    private var receptorSimulador: BroadcastReceiver? = null
    private var ultimaLectura: ProtocoloArduino.Lectura? = null
    private var versionArduino: String? = null
    private var puertaAbierta = false
    private var ultimoModoLed: ProtocoloArduino.ModoLed? = null
    private val presencia = DetectorPresencia()
    private val retiro = DetectorRetiro()
    private var fijarReferenciaEnMs = 0L
    private var robotDespierto = false
    private var ultimoSaludoMs = 0L
    private var hablandoSaludo = false
    private var indiceGrupoPanel = 0
    private var indiceTarjetaSignos = 1
    private lateinit var layoutRobot: View
    private lateinit var caraRobot: CaraRobotView
    private lateinit var tvBurbuja: TextView
    private lateinit var panelEstacion: View
    private lateinit var scrollHerramienta: ScrollView
    private lateinit var contenedorHerramienta: LinearLayout
    private lateinit var tvRobotArduino: TextView
    private lateinit var tvRobotEstado: TextView
    private lateinit var tvRobotTemp: TextView
    private lateinit var tvRobotHum: TextView
    private lateinit var tvRobotCalor: TextView
    private lateinit var tvRobotSismos: TextView
    private lateinit var btnRobotBotiquin: Button
    private lateinit var tvEstacionEstado: TextView
    private lateinit var tvEstacionDatos: TextView
    private lateinit var btnEstacionPuerta: Button
    private lateinit var tarjetaSignos: View
    // Interruptor de la pestaña Sismos. No se guarda: al abrir la app siempre vigila, por seguridad.
    private var deteccionActiva = true
    private var enPestanaSignos = false
    private var sismosEnPausa = false
    private lateinit var switchDeteccion: MaterialSwitch
    private lateinit var tvDetalleDeteccion: TextView

    /** Si se deja la pestaña de signos vitales abierta sin usar, vuelve a vigilar sismos. */
    private val inactividadRunnable = Runnable {
        if (enPestanaSignos && !medicionActiva) {
            navInferior.selectedItemId = R.id.nav_sismos
            Toast.makeText(this, "Volviendo a vigilar sismos", Toast.LENGTH_SHORT).show()
        }
    }

    // Biometría: respiración y pulso se miden por separado
    private var panelSignos = TipoMedicion.RESPIRACION
    private var tipoActual = TipoMedicion.RESPIRACION
    private var esMujer = false
    private var isBiometricMeasuring = false
    private var biometricStartTime = 0L
    private val tiemposNs = ArrayList<Long>()
    private val muestrasX = ArrayList<Float>()
    private val muestrasY = ArrayList<Float>()
    private val muestrasZ = ArrayList<Float>()
    private var biometricProgressRunnable: Runnable? = null
    private var cuentaRunnable: Runnable? = null
    private var ultimoAnalisisPulsoMs = 0L
    private var candidatoBpm: Int? = null
    private var lecturasSinMovimiento = 0
    private var sugerenciaPosicionMostrada = false

    // Monitor en vivo: últimos ~3 s del sensor a 50 puntos por segundo
    private val monitorX = FloatArray(MONITOR_PUNTOS)
    private val monitorY = FloatArray(MONITOR_PUNTOS)
    private val monitorZ = FloatArray(MONITOR_PUNTOS)
    private var monitorIndice = 0
    private var monitorCantidad = 0
    private var acumX = 0f
    private var acumY = 0f
    private var acumZ = 0f
    private var acumN = 0
    private var ultimoPuntoMonitorNs = 0L
    private var mediaX = Float.NaN
    private var mediaY = 0f
    private var mediaZ = 0f
    private var varX = 0f
    private var varY = 0f
    private var varZ = 0f
    private var ultimaCalidadMs = 0L

    // Avisos para quien no puede ver la pantalla (teléfono sobre el cuerpo)
    private var accionTrasVoz: (() -> Unit)? = null
    private var tts: TextToSpeech? = null
    private var ttsListo = false
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

    /** Mientras dura la alerta, la voz repite qué hacer cada 15 s (la sirena suena en bucle). */
    private val alarmaRunnable = object : Runnable {
        override fun run() {
            if (!alarmaSonando) return
            hablarAlerta(
                when (ultimoNivel) {
                    Nivel.FUERTE -> "Sismo fuerte. Agáchate, cúbrete y sujétate. Al terminar, evacúa."
                    Nivel.MODERADO -> "Sismo detectado. Agáchate, cúbrete y sujétate."
                    else -> "Sismo leve detectado. Mantén la calma y aléjate de las ventanas."
                }
            )
            if (ultimoNivel == Nivel.LEVE) alarmaSonando = false else handler.postDelayed(this, 15_000L)
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
        imgPulso = findViewById(R.id.imgPulso)
        tvInstruccionPulso = findViewById(R.id.tvInstruccionPulso)
        cajaResultadoRespiracion = findViewById(R.id.cajaResultadoRespiracion)
        cajaResultadoPulso = findViewById(R.id.cajaResultadoPulso)
        layoutMonitor = findViewById(R.id.layoutMonitor)
        graficaVital = findViewById(R.id.graficaVital)
        tvCuenta = findViewById(R.id.tvCuenta)
        ivCalidad = findViewById(R.id.ivCalidad)
        tvCalidad = findViewById(R.id.tvCalidad)
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

        btnSonido = findViewById(R.id.btnSonido)
        sonidos.efectosActivos = getPreferences(MODE_PRIVATE).getBoolean(PREF_SONIDOS, true)
        actualizarBotonSonido()
        btnSonido.setOnClickListener {
            sonidos.efectosActivos = !sonidos.efectosActivos
            getPreferences(MODE_PRIVATE).edit().putBoolean(PREF_SONIDOS, sonidos.efectosActivos).apply()
            actualizarBotonSonido()
            if (sonidos.efectosActivos) sonidos.efecto(SintetizadorSonidos.tic())
            Toast.makeText(
                this,
                if (sonidos.efectosActivos) "Sonidos activados" else "Sonidos silenciados (la alarma de sismo siempre suena)",
                Toast.LENGTH_SHORT
            ).show()
        }
        iniciarVoz()

        if (accelSensor == null && linearAccelSensor == null) {
            tvStatusBadge.text = "ESTE TELÉFONO NO TIENE ACELERÓMETRO"
            tvStatusBadge.setTextColor(Color.parseColor("#F87171"))
        }

        navInferior.setOnItemSelectedListener { item ->
            mostrarPestana(item.itemId)
            true
        }
        chipEstado.setOnClickListener { navInferior.selectedItemId = R.id.nav_sismos }

        switchDeteccion = findViewById(R.id.switchDeteccion)
        tvDetalleDeteccion = findViewById(R.id.tvDetalleDeteccion)
        switchDeteccion.setOnCheckedChangeListener { _, activa ->
            deteccionActiva = activa
            tvDetalleDeteccion.text = if (activa) {
                "Apágala antes de descolgar o manipular el teléfono"
            } else "Apagada: no se avisarán sismos. Enciéndela al volver a colocar el teléfono"
            actualizarPausaSismos(graciaMs = 0L)
            Toast.makeText(
                this, if (activa) "Detección de sismos activada" else "Detección de sismos desactivada", Toast.LENGTH_SHORT
            ).show()
        }

        btnRestablecer.setOnClickListener {
            detenerAlarma()
            try {
                tts?.stop()
            } catch (_: Exception) {
                // Ignorar
            }
            sonidos.efecto(SintetizadorSonidos.tic())
            finalizarSismo()
            detector.restablecer()
            ultimaFase = null
            ultimoAspectoEstado = null
        }

        grupoPanel.addOnButtonCheckedListener { _, id, marcado ->
            if (marcado) mostrarPanel(if (id == R.id.btnPanelPulso) TipoMedicion.PULSO else TipoMedicion.RESPIRACION)
        }
        grupoSexo.addOnButtonCheckedListener { _, id, marcado ->
            if (marcado) seleccionarSexo(id == R.id.btnMujer)
        }
        btnMedirRespiracion.setOnClickListener { alTocarMedir(TipoMedicion.RESPIRACION) }
        btnMedirPulso.setOnClickListener { alTocarMedir(TipoMedicion.PULSO) }
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
        iniciarEstacion()
        mostrarErrorAnteriorSiHay()
    }

    // ===================== NAVEGACIÓN =====================

    private fun mostrarPestana(itemId: Int) {
        scrollSismos.visibility = if (itemId == R.id.nav_sismos) View.VISIBLE else View.GONE
        scrollSignos.visibility = if (itemId == R.id.nav_signos) View.VISIBLE else View.GONE
        layoutHistorial.visibility = if (itemId == R.id.nav_historial) View.VISIBLE else View.GONE
        if (itemId == R.id.nav_historial) mostrarHistorial()

        // En la pestaña de signos vitales alguien está manipulando el teléfono: no es un sismo.
        enPestanaSignos = itemId == R.id.nav_signos
        actualizarPausaSismos(graciaMs = PAUSA_SISMOS_TRAS_PESTANA_MS)
        reiniciarInactividad()
    }

    private fun reiniciarInactividad() {
        handler.removeCallbacks(inactividadRunnable)
        if (enPestanaSignos) handler.postDelayed(inactividadRunnable, INACTIVIDAD_SIGNOS_MS)
    }

    override fun dispatchTouchEvent(ev: MotionEvent?): Boolean {
        if (enPestanaSignos && ev?.action == MotionEvent.ACTION_DOWN) reiniciarInactividad()
        return super.dispatchTouchEvent(ev)
    }

    /**
     * La detección se pausa si está apagada con el interruptor, si se está midiendo o si la
     * pestaña de signos vitales está abierta. Al reanudarse espera [graciaMs] antes de vigilar.
     */
    private fun actualizarPausaSismos(graciaMs: Long) {
        val pausar = !deteccionActiva || medicionActiva || enPestanaSignos || modoRobot == ModoRobot.HERRAMIENTA
        if (pausar) {
            if (!sismosEnPausa) pausarSismos()
            sismosEnPausa = true
            mostrarSismosPausados()
        } else if (sismosEnPausa) {
            sismosEnPausa = false
            reanudarSismosEnMs = SystemClock.elapsedRealtime() + graciaMs
            ultimaFase = null
            ultimoAspectoEstado = null
            ponerEstadoGlobal(R.drawable.ic_reloj, "Reanudando", "#94A3B8")
        }
    }

    private fun mostrarSismosPausados() {
        val desactivada = !deteccionActiva
        val gris = Color.parseColor("#94A3B8")
        ponerEstadoGlobal(R.drawable.ic_pausa, if (desactivada) "Sismos desactivados" else "Sismos en pausa", "#94A3B8")
        ivEstadoSismo.setImageResource(R.drawable.ic_pausa)
        ivEstadoSismo.imageTintList = ColorStateList.valueOf(gris)
        circuloEstado.backgroundTintList = ColorStateList.valueOf(conAlfa(gris, 0x26))
        tvStatusBadge.text = if (desactivada) "DETECCIÓN DESACTIVADA" else "DETECCIÓN EN PAUSA"
        tvStatusBadge.setTextColor(gris)
        tvNivelMovimiento.text = if (desactivada) "No se avisarán sismos" else "Se está usando el teléfono"
        tvAceleracionPico.text = "Aceleración actual: --"
        tvAceleracionPico.setTextColor(gris)
        tvQuake30sTimer.text = if (desactivada) {
            "Enciende \"Detección de sismos\" al volver a colocar el teléfono."
        } else "Se reanuda sola al terminar de medir y salir de Signos vitales."
        btnRestablecer.visibility = View.GONE
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
        if (::tvRobotEstado.isInitialized) {
            tvRobotEstado.text = texto
            tvRobotEstado.setTextColor(c)
            actualizarLedExterior()
        }
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
        // Con la detección apagada, durante una medición o en la pestaña de signos vitales
        // (y un rato después) el teléfono está en manos de una persona.
        if (sismosEnPausa || hablandoSaludo) return
        if (reanudarSismosEnMs > 0L) {
            if (SystemClock.elapsedRealtime() < reanudarSismosEnMs) return
            reanudarSismosEnMs = 0L
            detector.restablecer()
            ultimaFase = null
            ultimoAspectoEstado = null
        }
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
            alEmpezarSismoEnEstacion()
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
            val terminoSolo = sismoActual != null  // no fue con el botón "restablecer"
            finalizarSismo()
            if (terminoSolo) {
                alTerminarSismoEnEstacion()
                detenerAlarma()
                sonidos.alarma(SintetizadorSonidos.despejado())
                handler.postDelayed({ hablarAlerta("La sacudida terminó. Revisa si hay personas heridas.") }, 900L)
            }
        }
    }

    /** Guarda el sismo en el historial y limpia la pantalla principal. */
    private fun finalizarSismo() {
        val terminado = sismoActual ?: return
        guardarSismo(terminado)
        sismoActual = null
        mostrarEscalaRichter(null)
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

    /** Crea las filas de la escala de Richter una sola vez (inflarlas en cada actualización trababa la app). */
    private fun crearEscalaRichter() {
        listaRichter.removeAllViews()
        filasRichter.clear()
        for (categoria in EscalaSismica.CATEGORIAS_RICHTER) {
            val fila = layoutInflater.inflate(R.layout.item_richter, listaRichter, false)
            val nombre = fila.findViewById<TextView>(R.id.tvRichterNombre)
            val rango = fila.findViewById<TextView>(R.id.tvRichterRango)
            nombre.text = categoria.nombre
            rango.text = categoria.hasta?.let {
                String.format(Locale.getDefault(), "%.1f – %.1f", categoria.desde, it - 0.1)
            } ?: String.format(Locale.getDefault(), "%.1f o más", categoria.desde)
            listaRichter.addView(fila)
            filasRichter += FilaRichter(categoria, fila, nombre, rango)
        }
    }

    /**
     * Actualiza la tarjeta de Richter y resalta la categoría de [magnitud] (null = sin sismo).
     * Se llama varias veces por segundo durante una alerta: solo toca la pantalla si cambió algo.
     */
    private fun mostrarEscalaRichter(magnitud: Double?) {
        val redondeada = magnitud?.let { Math.round(it * 10) / 10.0 }
        if (filasRichter.isNotEmpty() && redondeada == magnitudMostrada && redondeada != null) return
        magnitudMostrada = redondeada
        val actual = magnitud?.let { EscalaSismica.categoriaRichter(it) }
        val color = Color.parseColor(if (actual != null) colorRichter(magnitud) else "#94A3B8")
        if (magnitud != null && actual != null) {
            tvMagnitud.text = String.format(Locale.getDefault(), "%.1f", magnitud)
            tvMagnitud.setTextColor(color)
            tvCategoriaRichter.text = actual.nombre
            tvDetalleRichter.text = "Magnitud estimada del sismo en curso"
        } else {
            tvMagnitud.text = "--"
            tvMagnitud.setTextColor(color)
            tvCategoriaRichter.text = "Sin sismo en curso"
            tvDetalleRichter.text = if (historial.sismos().isEmpty()) {
                "Se estima al confirmar un sismo"
            } else "El último sismo quedó guardado en el Historial"
        }
        if (filasRichter.isEmpty()) crearEscalaRichter()
        val tenue = Color.parseColor("#94A3B8")
        for (fila in filasRichter) {
            val resaltada = fila.categoria == actual
            fila.vista.backgroundTintList =
                ColorStateList.valueOf(if (resaltada) conAlfa(color, 0x30) else Color.TRANSPARENT)
            fila.nombre.setTextColor(if (resaltada) color else tenue)
            fila.rango.setTextColor(if (resaltada) color else tenue)
            fila.nombre.setTypeface(null, if (resaltada) android.graphics.Typeface.BOLD else android.graphics.Typeface.NORMAL)
            fila.rango.setTypeface(null, if (resaltada) android.graphics.Typeface.BOLD else android.graphics.Typeface.NORMAL)
        }
    }

    private class FilaRichter(
        val categoria: EscalaSismica.Categoria,
        val vista: View,
        val nombre: TextView,
        val rango: TextView,
    )

    private fun colorRichter(magnitud: Double) = when {
        magnitud >= 6.0 -> "#F87171"
        magnitud >= 4.0 -> "#FBBF24"
        else -> "#38BDF8"
    }

    private fun iniciarAlarma() {
        handler.removeCallbacks(alarmaRunnable)
        alarmaSonando = true
        when (ultimoNivel) {
            Nivel.FUERTE -> sonidos.iniciarSirena(rapida = true)
            Nivel.MODERADO -> sonidos.iniciarSirena(rapida = false)
            else -> {
                sonidos.detenerSirena()
                sonidos.alarma(SintetizadorSonidos.preAlerta())
            }
        }
        // La primera indicación por voz, apenas empieza la sirena.
        handler.postDelayed(alarmaRunnable, 900L)
    }

    private fun detenerAlarma() {
        alarmaSonando = false
        handler.removeCallbacks(alarmaRunnable)
        sonidos.detenerSirena()
    }

    // ===================== ESTACIÓN: ARDUINO Y MODO ROBOT =====================

    private fun iniciarEstacion() {
        layoutRobot = findViewById(R.id.layoutRobot)
        caraRobot = findViewById(R.id.caraRobot)
        tvBurbuja = findViewById(R.id.tvBurbuja)
        panelEstacion = findViewById(R.id.panelEstacion)
        scrollHerramienta = findViewById(R.id.scrollHerramienta)
        contenedorHerramienta = findViewById(R.id.contenedorHerramienta)
        tvRobotArduino = findViewById(R.id.tvRobotArduino)
        tvRobotEstado = findViewById(R.id.tvRobotEstado)
        tvRobotTemp = findViewById(R.id.tvRobotTemp)
        tvRobotHum = findViewById(R.id.tvRobotHum)
        tvRobotCalor = findViewById(R.id.tvRobotCalor)
        tvRobotSismos = findViewById(R.id.tvRobotSismos)
        btnRobotBotiquin = findViewById(R.id.btnRobotBotiquin)
        tvEstacionEstado = findViewById(R.id.tvEstacionEstado)
        tvEstacionDatos = findViewById(R.id.tvEstacionDatos)
        btnEstacionPuerta = findViewById(R.id.btnEstacionPuerta)
        tarjetaSignos = findViewById(R.id.tarjetaSignos)

        btnEstacionPuerta.setOnClickListener { abrirBotiquin(!puertaAbierta) }
        btnRobotBotiquin.setOnClickListener { abrirBotiquin(!puertaAbierta) }
        findViewById<Button>(R.id.btnModoRobot).setOnClickListener { entrarModoEstacion() }
        findViewById<Button>(R.id.btnRobotHerramienta).setOnClickListener {
            entrarModoHerramienta("Modo primeros auxilios. Elige respiración o pulso y sigue las indicaciones.")
        }
        findViewById<View>(R.id.btnSalirRobot).setOnClickListener { atrasEnModoRobot() }
        caraRobot.setOnClickListener {
            if (modoRobot == ModoRobot.ESTACION) despertar(saludar = true)
        }
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                if (modoRobot != ModoRobot.NINGUNO) {
                    atrasEnModoRobot()
                } else {
                    isEnabled = false
                    onBackPressedDispatcher.onBackPressed()
                    isEnabled = true
                }
            }
        })

        arduino = ConexionArduino(this, ::alMensajeArduino, ::alCambiarConexion)
        arduino.iniciar()
        registrarSimuladorSiDepuracion()
        actualizarTarjetaEstacion()
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        // Se conectó el Arduino mientras la app estaba abierta.
        if (intent.action == UsbManager.ACTION_USB_DEVICE_ATTACHED) arduino.conectar()
    }

    private fun alCambiarConexion(conectado: Boolean) {
        actualizarTarjetaEstacion()
        if (conectado) {
            ultimoModoLed = null
            actualizarLedExterior()
            arduino.enviar(ProtocoloArduino.comandoLuzInterior(puertaAbierta))
            // Conectado al cuerpo de la estación: modo robot (si no se está midiendo).
            if (!medicionActiva) entrarModoEstacion()
        } else {
            ultimaLectura = null
            presencia.reiniciar()
            actualizarPanelEstacion()
            // Se desconectó el cable con el robot en la pared: sacaron la cabeza.
            if (modoRobot == ModoRobot.ESTACION) {
                entrarModoHerramienta("Me sacaste de la estación. Modo primeros auxilios: elige respiración o pulso.")
            }
        }
    }

    private fun alMensajeArduino(mensaje: ProtocoloArduino.Mensaje) {
        when (mensaje) {
            is ProtocoloArduino.Mensaje.Datos -> {
                val nueva = mensaje.lectura
                val previa = ultimaLectura
                // El DHT a veces falla una lectura: se conserva el último valor válido.
                ultimaLectura = ProtocoloArduino.Lectura(
                    nueva.temperaturaC ?: previa?.temperaturaC,
                    nueva.humedad ?: previa?.humedad,
                    nueva.distanciaCm,
                )
                actualizarDatosEstacion()
                if (modoRobot == ModoRobot.ESTACION) {
                    val distancia = nueva.distanciaCm
                    if (distancia != null && distancia < 160f) programarSueno()
                    when (presencia.procesar(distancia, SystemClock.elapsedRealtime())) {
                        DetectorPresencia.Evento.LLEGO -> despertar(saludar = true)
                        DetectorPresencia.Evento.SE_FUE -> dormir()
                        null -> Unit
                    }
                }
            }
            is ProtocoloArduino.Mensaje.Hola -> {
                versionArduino = mensaje.version
                actualizarTarjetaEstacion()
            }
            is ProtocoloArduino.Mensaje.Puerta -> {
                puertaAbierta = mensaje.abierta
                actualizarBotonesBotiquin()
            }
        }
    }

    private fun abrirBotiquin(abrir: Boolean) {
        if (!arduino.conectado) {
            Toast.makeText(this, "Conecta el Arduino de la estación para mover la puerta", Toast.LENGTH_SHORT).show()
            return
        }
        puertaAbierta = abrir
        arduino.enviar(ProtocoloArduino.comandoPuerta(abrir))
        // La luz de adentro se enciende con la puerta abierta para ver el botiquín.
        arduino.enviar(ProtocoloArduino.comandoLuzInterior(abrir))
        actualizarBotonesBotiquin()
    }

    private fun actualizarBotonesBotiquin() {
        val texto = if (puertaAbierta) "Cerrar botiquín" else "Abrir botiquín"
        btnEstacionPuerta.text = texto
        btnRobotBotiquin.text = texto
    }

    /** El LED de afuera repite el estado de la detección para verlo desde lejos. */
    private fun actualizarLedExterior() {
        if (!::arduino.isInitialized || !arduino.conectado) return
        val modo = when {
            sismosEnPausa -> ProtocoloArduino.ModoLed.PAUSA
            ultimaFase == Fase.ALERTA -> ProtocoloArduino.ModoLed.ALERTA
            ultimaFase == Fase.EVALUANDO -> ProtocoloArduino.ModoLed.AVISO
            ultimaFase == Fase.CALIBRANDO -> ProtocoloArduino.ModoLed.PAUSA
            else -> ProtocoloArduino.ModoLed.NORMAL
        }
        if (modo == ultimoModoLed) return
        ultimoModoLed = modo
        arduino.enviar(ProtocoloArduino.comandoLedExterior(modo))
    }

    private fun actualizarTarjetaEstacion() {
        if (!::arduino.isInitialized) return
        if (arduino.conectado) {
            val version = versionArduino?.let { " · versión $it" }.orEmpty()
            tvEstacionEstado.text = "Arduino conectado$version"
            tvEstacionEstado.setTextColor(Color.parseColor("#22C55E"))
            tvRobotArduino.text = "Estación conectada"
            tvRobotArduino.setTextColor(Color.parseColor("#22C55E"))
        } else {
            tvEstacionEstado.text = "Sin conectar: conecta el Arduino por USB"
            tvEstacionEstado.setTextColor(Color.parseColor("#94A3B8"))
            tvRobotArduino.text = "Estación sin conectar"
            tvRobotArduino.setTextColor(Color.parseColor("#94A3B8"))
        }
        actualizarDatosEstacion()
    }

    private fun actualizarDatosEstacion() {
        val l = ultimaLectura
        val temp = l?.temperaturaC?.let { String.format(Locale.getDefault(), "%.1f °C", it) } ?: "--"
        val hum = l?.humedad?.let { "${it.roundToInt()} %" } ?: "--"
        val dist = l?.distanciaCm?.let { "${it.roundToInt()} cm" } ?: "--"
        tvEstacionDatos.text = "Temperatura $temp · Humedad $hum · Distancia $dist"
        actualizarPanelEstacion()
    }

    /** Datos del panel derecho del robot: clima, sensación térmica y sismos de hoy. */
    private fun actualizarPanelEstacion() {
        val l = ultimaLectura
        val t = l?.temperaturaC
        val h = l?.humedad
        tvRobotTemp.text = t?.let { "${it.roundToInt()}°" } ?: "--"
        tvRobotHum.text = h?.let { "${it.roundToInt()}%" } ?: "--"
        if (t != null && h != null) {
            val indice = AsistenteEstacion.indiceCalor(t, h)
            tvRobotCalor.text = "${indice.roundToInt()}°"
            tvRobotCalor.setTextColor(
                Color.parseColor(
                    when {
                        indice >= 41 -> "#F87171"
                        indice >= 32 -> "#FBBF24"
                        else -> "#F8FAFC"
                    }
                )
            )
        } else {
            tvRobotCalor.text = "--"
            tvRobotCalor.setTextColor(Color.parseColor("#F8FAFC"))
        }
        if (modoRobot != ModoRobot.NINGUNO) {
            val hoy = sismosDeHoy()
            tvRobotSismos.text = if (hoy.isEmpty()) {
                "Hoy: sin actividad sísmica"
            } else {
                val m = EscalaSismica.magnitudEstimada(hoy.maxBy { it.fechaMs }.pgaPicoBruto)
                val ultimo = m?.let { String.format(Locale.getDefault(), " · último ≈ %.1f", it) }.orEmpty()
                "Hoy: ${hoy.size} ${if (hoy.size == 1) "sismo" else "sismos"}$ultimo"
            }
        }
    }

    private fun sismosDeHoy(): List<RegistroSismo> {
        val inicioDelDia = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0); set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
        }.timeInMillis
        return historial.sismos().filter { it.fechaMs >= inicioDelDia }
    }

    // ---------- Modos del robot ----------

    /** Teléfono colocado en la pared: cara del robot, horizontal, despierta al acercarse alguien. */
    private fun entrarModoEstacion() {
        if (medicionActiva) return
        devolverTarjetaSignos()
        modoRobot = ModoRobot.ESTACION
        if (navInferior.selectedItemId != R.id.nav_sismos) navInferior.selectedItemId = R.id.nav_sismos
        mostrarCapaRobot(true)
        panelEstacion.visibility = View.VISIBLE
        scrollHerramienta.visibility = View.GONE
        presencia.reiniciar()
        retiro.olvidarReferencia()
        fijarReferenciaEnMs = SystemClock.elapsedRealtime() + 4_000L  // cuando el teléfono ya está quieto
        actualizarPausaSismos(graciaMs = PAUSA_SISMOS_TRAS_PESTANA_MS)
        actualizarPanelEstacion()
        dormir()
    }

    /** Cabeza fuera del cuerpo: signos vitales en horizontal, con la cara del robot. */
    private fun entrarModoHerramienta(anuncio: String) {
        modoRobot = ModoRobot.HERRAMIENTA
        mostrarCapaRobot(true)
        moverTarjetaSignosAlRobot()
        panelEstacion.visibility = View.GONE
        scrollHerramienta.visibility = View.VISIBLE
        actualizarPausaSismos(graciaMs = 0L)
        handler.removeCallbacks(dormirRunnable)
        robotDespierto = true
        brilloPantalla(1f)
        caraRobot.expresion = CaraRobotView.Expresion.DESPIERTO
        tvBurbuja.text = anuncio
        hablar(anuncio)
    }

    private fun salirModoRobot() {
        if (medicionActiva) cancelarMedicion("Medición detenida.")
        devolverTarjetaSignos()
        modoRobot = ModoRobot.NINGUNO
        handler.removeCallbacks(dormirRunnable)
        retiro.olvidarReferencia()
        mostrarCapaRobot(false)
        actualizarPausaSismos(graciaMs = PAUSA_SISMOS_TRAS_PESTANA_MS)
    }

    /** Botón de cerrar o "atrás": de herramienta vuelve a estación; de estación, al panel. */
    private fun atrasEnModoRobot() {
        when (modoRobot) {
            ModoRobot.HERRAMIENTA -> if (arduino.conectado) entrarModoEstacion() else salirModoRobot()
            ModoRobot.ESTACION -> salirModoRobot()
            ModoRobot.NINGUNO -> Unit
        }
    }

    private fun mostrarCapaRobot(visible: Boolean) {
        layoutRobot.visibility = if (visible) View.VISIBLE else View.GONE
        requestedOrientation = if (visible) {
            ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
        } else ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
        val barras = WindowCompat.getInsetsController(window, window.decorView)
        if (visible) {
            barras.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            barras.hide(WindowInsetsCompat.Type.systemBars())
        } else {
            barras.show(WindowInsetsCompat.Type.systemBars())
            brilloPantalla(null)
        }
    }

    /** null = brillo normal del sistema. */
    private fun brilloPantalla(nivel: Float?) {
        window.attributes = window.attributes.apply {
            screenBrightness = nivel ?: WindowManager.LayoutParams.BRIGHTNESS_OVERRIDE_NONE
        }
    }

    private fun moverTarjetaSignosAlRobot() {
        if (tarjetaSignos.parent == contenedorHerramienta) return
        val origen = tarjetaSignos.parent as ViewGroup
        indiceGrupoPanel = origen.indexOfChild(grupoPanel)
        indiceTarjetaSignos = origen.indexOfChild(tarjetaSignos)
        origen.removeView(grupoPanel)
        origen.removeView(tarjetaSignos)
        contenedorHerramienta.addView(grupoPanel)
        contenedorHerramienta.addView(tarjetaSignos)
    }

    private fun devolverTarjetaSignos() {
        if (tarjetaSignos.parent != contenedorHerramienta) return
        contenedorHerramienta.removeView(grupoPanel)
        contenedorHerramienta.removeView(tarjetaSignos)
        val destino = findViewById<ViewGroup>(R.id.layoutBiometricSection)
        destino.addView(grupoPanel, indiceGrupoPanel.coerceIn(0, destino.childCount))
        destino.addView(tarjetaSignos, indiceTarjetaSignos.coerceIn(0, destino.childCount))
    }

    private fun scrollDeSignos(): ScrollView = if (modoRobot == ModoRobot.HERRAMIENTA) scrollHerramienta else scrollSignos

    // ---------- Despertar, saludar y dormir ----------

    private fun despertar(saludar: Boolean) {
        robotDespierto = true
        brilloPantalla(1f)
        if (caraRobot.expresion == CaraRobotView.Expresion.DORMIDO) caraRobot.expresion = CaraRobotView.Expresion.DESPIERTO
        programarSueno()
        val ahora = SystemClock.elapsedRealtime()
        if (saludar && modoRobot == ModoRobot.ESTACION && ultimaFase != Fase.ALERTA &&
            (ultimoSaludoMs == 0L || ahora - ultimoSaludoMs > ESPERA_ENTRE_SALUDOS_MS)
        ) {
            ultimoSaludoMs = ahora
            val texto = AsistenteEstacion.saludo(
                AsistenteEstacion.Datos(
                    temperaturaC = ultimaLectura?.temperaturaC,
                    humedad = ultimaLectura?.humedad,
                    horaDelDia = Calendar.getInstance().get(Calendar.HOUR_OF_DAY),
                    sismosHoy = sismosDeHoy(),
                    deteccionActiva = deteccionActiva,
                )
            )
            tvBurbuja.text = texto
            caraRobot.expresion = CaraRobotView.Expresion.FELIZ
            handler.postDelayed({
                if (caraRobot.expresion == CaraRobotView.Expresion.FELIZ) caraRobot.expresion = CaraRobotView.Expresion.DESPIERTO
            }, 1_500L)
            // El parlante hace vibrar el teléfono: mientras saluda no se evalúan sismos.
            if (hablar(texto, ID_VOZ_SALUDO)) {
                hablandoSaludo = true
                handler.postDelayed({ terminarSaludo() }, 30_000L)  // por si la voz nunca avisa
            }
        }
    }

    private fun terminarSaludo() {
        if (!hablandoSaludo) return
        hablandoSaludo = false
        reanudarSismosEnMs = SystemClock.elapsedRealtime() + 1_500L
    }

    private fun dormir() {
        if (modoRobot != ModoRobot.ESTACION || ultimaFase == Fase.ALERTA || presencia.presente) return
        robotDespierto = false
        caraRobot.expresion = CaraRobotView.Expresion.DORMIDO
        tvBurbuja.text = ""
        brilloPantalla(0.04f)
    }

    private val dormirRunnable = Runnable { dormir() }

    private fun programarSueno() {
        handler.removeCallbacks(dormirRunnable)
        if (modoRobot == ModoRobot.ESTACION) handler.postDelayed(dormirRunnable, ESPERA_PARA_DORMIR_MS)
    }

    /** Ante un sismo: el robot despierta, abre el botiquín y enciende la luz interior. */
    private fun alEmpezarSismoEnEstacion() {
        if (modoRobot == ModoRobot.ESTACION) {
            robotDespierto = true
            brilloPantalla(1f)
            handler.removeCallbacks(dormirRunnable)
            tvBurbuja.text = "¡Sismo! Agáchate, cúbrete y sujétate. El botiquín está abierto."
        }
        caraRobot.expresion = CaraRobotView.Expresion.ALERTA
        if (arduino.conectado && !puertaAbierta) abrirBotiquin(true)
        actualizarLedExterior()
        actualizarPanelEstacion()
    }

    private fun alTerminarSismoEnEstacion() {
        caraRobot.expresion = CaraRobotView.Expresion.PREOCUPADO
        tvBurbuja.text = "La sacudida terminó. Revisa si hay personas heridas. El botiquín sigue abierto."
        actualizarPanelEstacion()
        programarSueno()
    }

    /** Solo en compilaciones de depuración: permite simular el Arduino desde la computadora. */
    private fun registrarSimuladorSiDepuracion() {
        if (applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE == 0) return
        val receptor = object : BroadcastReceiver() {
            override fun onReceive(context: Context, intent: Intent) {
                intent.getStringExtra("linea")?.let { arduino.simularLinea(it) }
                if (intent.hasExtra("conectado")) arduino.simularConexion(intent.getBooleanExtra("conectado", false))
            }
        }
        ContextCompat.registerReceiver(
            this, receptor, IntentFilter(ACCION_SIMULAR_ARDUINO), ContextCompat.RECEIVER_EXPORTED
        )
        receptorSimulador = receptor
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

    /** El botón de medir se convierte en "Detener" mientras se mide. */
    private fun alTocarMedir(tipo: TipoMedicion) {
        if (medicionActiva) cancelarMedicion("Medición detenida.") else iniciarMedicion(tipo)
    }

    private fun iniciarMedicion(tipo: TipoMedicion) {
        if (medicionActiva || accelSensor == null) return
        medicionActiva = true
        tipoActual = tipo
        medicionId = System.currentTimeMillis()
        intentoPulso = 0
        actualizarPausaSismos(graciaMs = PAUSA_SISMOS_TRAS_MEDIR_MS)
        mostrarInstrucciones(false)
        actualizarBotonesMedicion()
        if (tipo == TipoMedicion.RESPIRACION) {
            mostrarValor(tvRpmValues, tvRpmEstado, "-- RPM", "Preparando…", "#38BDF8")
        } else {
            mostrarValor(tvBpmValues, tvBpmEstado, "-- BPM", "Preparando…", "#F43F5E")
        }
        iniciarCuentaRegresiva()
    }

    /**
     * 3 segundos para colocar el teléfono y quedarse quieto antes de medir: así el movimiento
     * de acomodarse no entra en la medición. Avisa con voz y pitidos porque la persona no ve
     * la pantalla con el teléfono sobre el cuerpo.
     */
    private fun iniciarCuentaRegresiva() {
        layoutMonitor.visibility = View.VISIBLE
        graficaVital.limpiar()
        ponerCalidad(R.drawable.ic_reloj, "Coloca el teléfono y quédate quieto", "#94A3B8")
        hablar("Coloca el teléfono y quédate quieto")
        var segundos = SEGUNDOS_PREPARACION
        val paso = object : Runnable {
            override fun run() {
                if (!medicionActiva) return
                if (segundos > 0) {
                    tvCuenta.text = segundos.toString()
                    ponerEstadoMedicion("Empieza en $segundos…", Tono.PROGRESO)
                    sonidos.efecto(SintetizadorSonidos.tic())
                    segundos--
                    handler.postDelayed(this, 1000L)
                } else {
                    sonidos.efecto(SintetizadorSonidos.inicio())
                    // Pausa para que el sonido termine antes de empezar a registrar.
                    handler.postDelayed({ if (medicionActiva) capturar() }, 450L)
                }
            }
        }
        cuentaRunnable = paso
        handler.post(paso)
    }

    private fun mostrarInstrucciones(visibles: Boolean) {
        val v = if (visibles) View.VISIBLE else View.GONE
        grupoSexo.visibility = v
        imgRespiracion.visibility = v
        tvInstruccionRespiracion.visibility = v
        imgPulso.visibility = v
        tvInstruccionPulso.visibility = v
        if (!visibles) scrollDeSignos().let { s -> s.post { s.smoothScrollTo(0, 0) } }
    }

    private fun actualizarBotonesMedicion() {
        val hayAcelerometro = accelSensor != null
        configurarBotonMedir(
            btnMedirRespiracion, medicionActiva && tipoActual == TipoMedicion.RESPIRACION,
            "Medir respiración · 20 s", "#0284C7"
        )
        configurarBotonMedir(btnMedirPulso, medicionActiva && tipoActual == TipoMedicion.PULSO, "Medir pulso", "#BE123C")
        btnMedirRespiracion.isEnabled = hayAcelerometro
        btnMedirPulso.isEnabled = hayAcelerometro
        btnPanelRespiracion.isEnabled = !medicionActiva
        btnPanelPulso.isEnabled = !medicionActiva
        btnHombre.isEnabled = !medicionActiva
        btnMujer.isEnabled = !medicionActiva
    }

    private fun configurarBotonMedir(boton: MaterialButton, midiendo: Boolean, texto: String, color: String) {
        boton.text = if (midiendo) "Detener medición" else texto
        boton.setIconResource(if (midiendo) R.drawable.ic_detener else R.drawable.ic_iniciar)
        boton.backgroundTintList = ColorStateList.valueOf(Color.parseColor(if (midiendo) "#475569" else color))
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
        actualizarPausaSismos(graciaMs = PAUSA_SISMOS_TRAS_MEDIR_MS)
        reiniciarInactividad()
    }

    private fun capturar() {
        val sensor = accelSensor ?: return cancelarMedicion("Este teléfono no tiene acelerómetro.")
        val respiracion = tipoActual == TipoMedicion.RESPIRACION

        isBiometricMeasuring = true
        biometricStartTime = SystemClock.elapsedRealtime()
        tiemposNs.clear()
        muestrasX.clear()
        muestrasY.clear()
        muestrasZ.clear()
        reiniciarMonitor()
        ultimoAnalisisPulsoMs = 0L
        candidatoBpm = null
        lecturasSinMovimiento = 0
        sugerenciaPosicionMostrada = false

        // Más muestras por segundo para poder ver la vibración de cada latido.
        sensorManager.unregisterListener(this, sensor)
        sensorManager.registerListener(this, sensor, PERIODO_BIOMETRIA_US)

        pbBiometricProgress.visibility = View.VISIBLE
        pbBiometricProgress.progress = 0
        if (respiracion) {
            mostrarValor(tvRpmValues, tvRpmEstado, "-- RPM", "Midiendo…", "#38BDF8")
            ponerEstadoMedicion("Midiendo la respiración… respira normal", Tono.PROGRESO)
        } else {
            mostrarValor(tvBpmValues, tvBpmEstado, "-- BPM", "Midiendo…", "#F43F5E")
            ponerEstadoMedicion("Midiendo el pulso… no hables ni te muevas", Tono.PROGRESO)
        }

        biometricProgressRunnable?.let { handler.removeCallbacks(it) }
        biometricProgressRunnable = object : Runnable {
            override fun run() {
                if (!isBiometricMeasuring) return
                val transcurrido = SystemClock.elapsedRealtime() - biometricStartTime
                if (respiracion) {
                    pbBiometricProgress.progress = ((transcurrido * 100) / DURACION_RESPIRACION_MS).toInt().coerceIn(0, 100)
                    tvCuenta.text = "${((DURACION_RESPIRACION_MS - transcurrido + 999) / 1000).coerceAtLeast(0)} s"
                } else {
                    // El pulso se mide sin límite: la barra muestra cuánto falta para el primer análisis.
                    pbBiometricProgress.progress = ((transcurrido * 100) / PRIMER_ANALISIS_PULSO_MS).toInt().coerceIn(0, 100)
                    tvCuenta.text = "${transcurrido / 1000} s"
                }
                actualizarCalidad()

                if (respiracion && transcurrido >= DURACION_RESPIRACION_MS) {
                    alTerminarRespiracion()
                    return
                }
                if (!respiracion && transcurrido >= PRIMER_ANALISIS_PULSO_MS &&
                    transcurrido - ultimoAnalisisPulsoMs >= INTERVALO_ANALISIS_PULSO_MS
                ) {
                    ultimoAnalisisPulsoMs = transcurrido
                    analizarPulsoParcial(transcurrido)
                    if (!isBiometricMeasuring) return
                }
                handler.postDelayed(this, 100)
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
        cuentaRunnable?.let { handler.removeCallbacks(it) }
        cuentaRunnable = null
        layoutMonitor.visibility = View.GONE
        mostrarInstrucciones(true)
        actualizarBotonesMedicion()
        reanudarSismos()
        // Al reaparecer las instrucciones, llevar la vista al resultado.
        val caja = if (tipoActual == TipoMedicion.RESPIRACION) cajaResultadoRespiracion else cajaResultadoPulso
        val scroll = scrollDeSignos()
        scroll.post {
            val zona = Rect()
            caja.getDrawingRect(zona)
            scroll.offsetDescendantRectToMyCoords(caja, zona)
            scroll.smoothScrollTo(0, (zona.top - 40).coerceAtLeast(0))
        }
    }

    private fun alTerminarRespiracion() {
        detenerCaptura()
        val resultado = analizarMuestras(desdeNs = null)
        val rojo = "#F87171"
        val posicion = if (esMujer) "pecho (mujer)" else "abdomen (hombre)"
        when (resultado.estado) {
            EstadoMedicion.MUESTRA_INSUFICIENTE -> {
                mostrarValor(tvRpmValues, tvRpmEstado, "-- RPM", "Sin medición", rojo)
                mostrarFinal("Muestra insuficiente. Intenta de nuevo.", Tono.ERROR, "No se pudo medir. Intenta de nuevo.")
                guardarMedicion(null, null, "Muestra insuficiente")
            }

            EstadoMedicion.SIN_MOVIMIENTO -> {
                mostrarValor(tvRpmValues, tvRpmEstado, "0 RPM", "Sin movimiento", rojo)
                mostrarFinal(
                    "No se detectó la respiración. ¿Está el teléfono sobre una mesa?", Tono.ERROR,
                    "No se detectó la respiración."
                )
                guardarMedicion(null, null, "Sin movimiento · $posicion")
            }

            EstadoMedicion.MUCHO_MOVIMIENTO -> {
                mostrarValor(tvRpmValues, tvRpmEstado, "-- RPM", "Repetir", rojo)
                mostrarFinal(
                    "Hubo demasiado movimiento. Quédate quieto y repite.", Tono.AVISO,
                    "Hubo demasiado movimiento. Repite la medición."
                )
                guardarMedicion(null, null, "Demasiado movimiento · $posicion")
            }

            EstadoMedicion.RESPIRACION_IRREGULAR, EstadoMedicion.OK -> {
                val rpm = resultado.rpm
                if (rpm == null) {
                    mostrarValor(tvRpmValues, tvRpmEstado, "-- RPM", "Irregular, no se pudo medir", "#FBBF24")
                    mostrarFinal(
                        "La respiración fue irregular. Intenta de nuevo, respirando normal.", Tono.AVISO,
                        "La respiración fue irregular. Intenta de nuevo."
                    )
                    guardarMedicion(null, null, "Respiración irregular · $posicion")
                } else {
                    val estado = mostrarRpm(rpm)
                    mostrarFinal(
                        "Respiración medida · valor orientativo", Tono.OK,
                        "Respiración: $rpm por minuto. ${estado.substringBefore(" (")}."
                    )
                    guardarMedicion(rpm, null, "$estado · $posicion")
                }
            }
        }
    }

    /**
     * El pulso se mide de forma continua: cada 2 s se analizan los últimos 20 s. Un pulso con
     * periodicidad clara se acepta de inmediato; uno menos claro necesita que el siguiente
     * análisis coincida (±6 lpm). Se detiene solo si el teléfono deja de estar sobre una persona.
     */
    private fun analizarPulsoParcial(transcurridoMs: Long) {
        intentoPulso++
        val ultimo = tiemposNs.lastOrNull() ?: return
        val resultado = analizarMuestras(desdeNs = ultimo - VENTANA_PULSO_NS)
        recortarMuestras(ultimo - 2 * VENTANA_PULSO_NS)

        if (resultado.estado == EstadoMedicion.SIN_MOVIMIENTO) lecturasSinMovimiento++ else lecturasSinMovimiento = 0
        val bpm = resultado.bpm
        val previo = candidatoBpm
        when {
            bpm != null && (resultado.pulsoConfiable || (previo != null && kotlin.math.abs(bpm - previo) <= 6)) -> {
                val final = if (resultado.pulsoConfiable || previo == null) bpm else (bpm + previo) / 2
                detenerCaptura()
                val estado = mostrarBpm(final)
                val segundos = transcurridoMs / 1000
                mostrarFinal("Pulso medido en $segundos s · valor orientativo", Tono.OK, "Pulso: $final latidos por minuto.") {
                    // Después de decir el resultado, se escuchan unos latidos a ese ritmo.
                    sonidos.efecto(SintetizadorSonidos.latidos(final, 6))
                }
                guardarMedicion(null, final, estado)
            }

            bpm != null -> {
                candidatoBpm = bpm
                mostrarValor(tvBpmValues, tvBpmEstado, "≈ $bpm BPM", "Confirmando…", "#FBBF24")
                ponerEstadoMedicion("Posible pulso de $bpm. Confirmando… no te muevas", Tono.REINTENTO)
            }

            lecturasSinMovimiento >= 3 -> {
                detenerCaptura()
                mostrarValor(tvBpmValues, tvBpmEstado, "-- BPM", "Teléfono fuera del pecho", "#F87171")
                mostrarFinal(
                    "El teléfono no parece estar sobre el pecho. Colócalo y vuelve a medir.", Tono.ERROR,
                    "El teléfono no está sobre el pecho."
                )
                guardarMedicion(null, null, "Pulso no detectado (teléfono fuera del pecho)")
            }

            resultado.estado != EstadoMedicion.MUESTRA_INSUFICIENTE &&
                resultado.frecuenciaMuestreoHz in 1.0..FRECUENCIA_MIN_PULSO_HZ -> {
                detenerCaptura()
                mostrarValor(tvBpmValues, tvBpmEstado, "-- BPM", "Sensor muy lento", "#FBBF24")
                mostrarFinal(
                    "El sensor de este teléfono es demasiado lento para medir el pulso.", Tono.AVISO,
                    "El sensor es demasiado lento para medir el pulso."
                )
                guardarMedicion(null, null, "Sensor muy lento para el pulso")
            }

            else -> {
                candidatoBpm = null
                val motivo = if (resultado.estado == EstadoMedicion.MUCHO_MOVIMIENTO) {
                    "Hubo movimiento: quédate quieto"
                } else "Buscando el pulso… no te muevas"
                mostrarValor(tvBpmValues, tvBpmEstado, "-- BPM", "Buscando…", "#F43F5E")
                ponerEstadoMedicion(motivo, Tono.REINTENTO)
                // Sin sonido aquí: se está registrando y la vibración del parlante ensuciaría la señal.
                if (transcurridoMs >= SUGERENCIA_POSICION_MS) sugerenciaPosicionMostrada = true
                if (sugerenciaPosicionMostrada) {
                    ponerEstadoMedicion(
                        "Aún no aparece: mueve el teléfono un poco hacia el centro del pecho y quédate quieto",
                        Tono.AVISO
                    )
                }
            }
        }
    }

    private fun analizarMuestras(desdeNs: Long?): AnalizadorVital.Resultado {
        val inicio = if (desdeNs == null) 0 else tiemposNs.indexOfFirst { it >= desdeNs }.coerceAtLeast(0)
        return AnalizadorVital.analizar(
            LongArray(tiemposNs.size - inicio) { tiemposNs[inicio + it] },
            FloatArray(muestrasX.size - inicio) { muestrasX[inicio + it] },
            FloatArray(muestrasY.size - inicio) { muestrasY[inicio + it] },
            FloatArray(muestrasZ.size - inicio) { muestrasZ[inicio + it] },
        )
    }

    /** Descarta muestras viejas para que la medición continua no crezca sin límite. */
    private fun recortarMuestras(antesDeNs: Long) {
        val corte = tiemposNs.indexOfFirst { it >= antesDeNs }
        if (corte <= 0) return
        tiemposNs.subList(0, corte).clear()
        muestrasX.subList(0, corte).clear()
        muestrasY.subList(0, corte).clear()
        muestrasZ.subList(0, corte).clear()
    }

    private fun cancelarMedicion(mensaje: String) {
        val estabaMidiendo = medicionActiva
        if (isBiometricMeasuring) detenerCaptura()
        if (estabaMidiendo) terminarMedicion()
        ponerEstadoMedicion(mensaje, Tono.NEUTRO)
        if (estabaMidiendo) {
            sonidos.efecto(SintetizadorSonidos.detenido())
            if (tipoActual == TipoMedicion.RESPIRACION) {
                mostrarValor(tvRpmValues, tvRpmEstado, "-- RPM", "Detenida", "#94A3B8")
            } else {
                mostrarValor(tvBpmValues, tvBpmEstado, "-- BPM", "Detenida", "#94A3B8")
                if (intentoPulso > 0) guardarMedicion(null, null, "Pulso detenido antes de detectarlo")
            }
        }
    }

    /**
     * Termina la medición, muestra el mensaje y lo anuncia con un sonido y en voz alta.
     * [despuesDeHablar] se ejecuta al terminar la voz (o enseguida si no hay voz).
     */
    private fun mostrarFinal(mensaje: String, tono: Tono, enVozAlta: String, despuesDeHablar: (() -> Unit)? = null) {
        terminarMedicion()
        ponerEstadoMedicion(mensaje, tono)
        val sonido = if (tono == Tono.OK) SintetizadorSonidos.exito() else SintetizadorSonidos.error()
        sonidos.efecto(sonido)
        handler.postDelayed({
            accionTrasVoz = despuesDeHablar
            if (!hablar(enVozAlta, ID_VOZ_RESULTADO)) {
                accionTrasVoz = null
                despuesDeHablar?.invoke()
            }
        }, SintetizadorSonidos.duracionMs(sonido) + 150L)
    }

    private fun ponerEstadoMedicion(mensaje: String, tono: Tono) {
        val c = Color.parseColor(tono.color)
        tvBiometricStatus.text = mensaje
        tvBiometricStatus.setTextColor(c)
        ivEstadoMedicion.setImageResource(tono.icono)
        ivEstadoMedicion.imageTintList = ColorStateList.valueOf(c)
        if (modoRobot == ModoRobot.HERRAMIENTA) {
            tvBurbuja.text = mensaje
            caraRobot.expresion = when (tono) {
                Tono.PROGRESO -> CaraRobotView.Expresion.ATENTO
                Tono.OK -> CaraRobotView.Expresion.FELIZ
                Tono.REINTENTO, Tono.AVISO, Tono.ERROR -> CaraRobotView.Expresion.PREOCUPADO
                Tono.NEUTRO -> CaraRobotView.Expresion.DESPIERTO
            }
        }
    }

    // ---------- Monitor en vivo ----------

    private fun reiniciarMonitor() {
        graficaVital.limpiar()
        graficaVital.rango = 0.25f
        monitorIndice = 0
        monitorCantidad = 0
        acumX = 0f; acumY = 0f; acumZ = 0f; acumN = 0
        ultimoPuntoMonitorNs = 0L
        mediaX = Float.NaN
    }

    /** Promedia el sensor a ~50 puntos por segundo y dibuja el movimiento del pecho. */
    private fun alimentarMonitor(tiempoNs: Long, x: Float, y: Float, z: Float) {
        acumX += x; acumY += y; acumZ += z; acumN++
        if (tiempoNs - ultimoPuntoMonitorNs < 20_000_000L) return
        ultimoPuntoMonitorNs = tiempoNs
        val px = acumX / acumN
        val py = acumY / acumN
        val pz = acumZ / acumN
        acumX = 0f; acumY = 0f; acumZ = 0f; acumN = 0

        monitorX[monitorIndice] = px
        monitorY[monitorIndice] = py
        monitorZ[monitorIndice] = pz
        monitorIndice = (monitorIndice + 1) % MONITOR_PUNTOS
        if (monitorCantidad < MONITOR_PUNTOS) monitorCantidad++

        // Se dibuja el eje que más se mueve, respecto de su promedio lento.
        if (mediaX.isNaN()) {
            mediaX = px; mediaY = py; mediaZ = pz
        }
        val a = 0.03f
        mediaX += a * (px - mediaX); mediaY += a * (py - mediaY); mediaZ += a * (pz - mediaZ)
        val dx = px - mediaX
        val dy = py - mediaY
        val dz = pz - mediaZ
        varX += a * (dx * dx - varX); varY += a * (dy * dy - varY); varZ += a * (dz * dz - varZ)
        val valor = when {
            varX >= varY && varX >= varZ -> dx
            varY >= varZ -> dy
            else -> dz
        }
        graficaVital.agregarPunto(valor)
    }

    private fun actualizarCalidad() {
        val ahora = SystemClock.elapsedRealtime()
        if (ahora - ultimaCalidadMs < 500L) return
        ultimaCalidadMs = ahora
        val n = monitorCantidad
        val calidad = AnalizadorVital.calidadSenal(
            FloatArray(n) { monitorX[it] }, FloatArray(n) { monitorY[it] }, FloatArray(n) { monitorZ[it] }
        )
        when (calidad) {
            null -> ponerCalidad(R.drawable.ic_reloj, "Leyendo el sensor…", "#94A3B8")
            AnalizadorVital.CalidadSenal.SIN_MOVIMIENTO ->
                ponerCalidad(R.drawable.ic_alerta, "No se nota movimiento: ¿el teléfono está sobre el cuerpo?", "#FBBF24")
            AnalizadorVital.CalidadSenal.MUCHO_MOVIMIENTO ->
                ponerCalidad(R.drawable.ic_alerta, "Demasiado movimiento: quédate quieto", "#F87171")
            AnalizadorVital.CalidadSenal.BUENA -> ponerCalidad(
                R.drawable.ic_check_circulo,
                if (tipoActual == TipoMedicion.RESPIRACION) "Se nota la respiración: vas bien"
                else "Teléfono sobre el cuerpo: buscando latidos",
                "#22C55E"
            )
        }
    }

    private fun ponerCalidad(icono: Int, texto: String, color: String) {
        val c = Color.parseColor(color)
        tvCalidad.text = texto
        tvCalidad.setTextColor(c)
        ivCalidad.setImageResource(icono)
        ivCalidad.imageTintList = ColorStateList.valueOf(c)
    }

    // ---------- Sonido y voz (solo fuera de la captura: la vibración del parlante ensucia la señal) ----------

    private fun actualizarBotonSonido() {
        btnSonido.setImageResource(if (sonidos.efectosActivos) R.drawable.ic_volumen else R.drawable.ic_volumen_apagado)
        btnSonido.contentDescription = if (sonidos.efectosActivos) "Silenciar sonidos" else "Activar sonidos"
        btnSonido.imageTintList = ColorStateList.valueOf(
            Color.parseColor(if (sonidos.efectosActivos) "#CBD5E1" else "#64748B")
        )
    }

    /** Voz de la app (respeta el botón de silencio). Devuelve false si no se pudo hablar. */
    private fun hablar(texto: String, id: String = "ehria"): Boolean {
        if (!ttsListo || isBiometricMeasuring || !sonidos.efectosActivos) return false
        return try {
            tts?.speak(texto, TextToSpeech.QUEUE_FLUSH, null, id) == TextToSpeech.SUCCESS
        } catch (_: Exception) {
            false
        }
    }

    /** Voz de emergencia: suena aunque los sonidos estén silenciados. */
    private fun hablarAlerta(texto: String) {
        if (!ttsListo) return
        try {
            tts?.speak(texto, TextToSpeech.QUEUE_FLUSH, null, ID_VOZ_ALERTA)
        } catch (_: Exception) {
            // Ignorar
        }
    }

    private fun iniciarVoz() {
        tts = TextToSpeech(this) { estado ->
            if (estado != TextToSpeech.SUCCESS) return@TextToSpeech
            val motor = tts ?: return@TextToSpeech
            ttsListo = listOf("es-419", "es-US", "es-ES", "es").any { etiqueta ->
                val r = motor.setLanguage(Locale.forLanguageTag(etiqueta))
                r != TextToSpeech.LANG_MISSING_DATA && r != TextToSpeech.LANG_NOT_SUPPORTED
            }
            motor.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
                // La sirena baja mientras habla la voz de alerta, para que se entienda.
                override fun onStart(id: String?) {
                    handler.post {
                        caraRobot.hablando = true
                        if (id == ID_VOZ_ALERTA) sonidos.volumenSirena(0.25f)
                    }
                }

                override fun onDone(id: String?) {
                    handler.post {
                        caraRobot.hablando = false
                        if (id == ID_VOZ_SALUDO) terminarSaludo()
                        if (id == ID_VOZ_ALERTA) sonidos.volumenSirena(1f)
                        if (id == ID_VOZ_RESULTADO) {
                            accionTrasVoz?.invoke()
                            accionTrasVoz = null
                        }
                    }
                }

                @Deprecated("Deprecated in Java")
                override fun onError(id: String?) {
                    handler.post {
                        caraRobot.hablando = false
                        if (id == ID_VOZ_SALUDO) terminarSaludo()
                        sonidos.volumenSirena(1f)
                    }
                }
            })
        }
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
                    color = magnitud?.let { colorRichter(it) } ?: colorNivel(s.nivel),
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
                val reintentos = if (m.intentosPulso > 1) " · ${m.intentosPulso} análisis" else ""
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
        sonidos.detenerTodo()
        if (::arduino.isInitialized) arduino.detener()
        receptorSimulador?.let {
            try {
                unregisterReceiver(it)
            } catch (_: Exception) {
                // Ignorar
            }
        }
        tts?.shutdown()
        tts = null
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

        // 2. ¿SACARON O COLOCARON EL TELÉFONO EN LA ESTACIÓN? (dirección de la gravedad)
        if (sensorType == Sensor.TYPE_ACCELEROMETER && !isBiometricMeasuring) vigilarRetiro(event)

        // 3. CAPTURA DE MUESTRAS PARA BIOMETRÍA (los tres ejes, con su hora exacta)
        if (sensorType == Sensor.TYPE_ACCELEROMETER && isBiometricMeasuring) {
            tiemposNs.add(event.timestamp)
            muestrasX.add(event.values[0])
            muestrasY.add(event.values[1])
            muestrasZ.add(event.values[2])
            alimentarMonitor(event.timestamp, event.values[0], event.values[1], event.values[2])
        }
    }

    private fun vigilarRetiro(event: SensorEvent) {
        if (modoRobot == ModoRobot.NINGUNO) return
        val ahora = SystemClock.elapsedRealtime()
        val evento = retiro.procesar(event.values[0], event.values[1], event.values[2], ahora)
        if (modoRobot == ModoRobot.ESTACION && fijarReferenciaEnMs > 0L && ahora >= fijarReferenciaEnMs) {
            fijarReferenciaEnMs = 0L
            retiro.fijarReferencia()
            return
        }
        when (evento) {
            DetectorRetiro.Evento.RETIRADO -> if (modoRobot == ModoRobot.ESTACION) {
                entrarModoHerramienta("Me sacaste de la estación. Modo primeros auxilios: elige respiración o pulso.")
            }
            DetectorRetiro.Evento.COLOCADO -> if (modoRobot == ModoRobot.HERRAMIENTA && !medicionActiva) {
                entrarModoEstacion()
            }
            null -> Unit
        }
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {
        // No usado
    }

    private companion object {
        const val DURACION_RESPIRACION_MS = 20_000L
        const val SEGUNDOS_PREPARACION = 3
        const val PRIMER_ANALISIS_PULSO_MS = 12_000L
        const val INTERVALO_ANALISIS_PULSO_MS = 2_000L
        const val VENTANA_PULSO_NS = 20_000_000_000L
        const val SUGERENCIA_POSICION_MS = 30_000L
        const val MONITOR_PUNTOS = 150
        const val PAUSA_SISMOS_TRAS_MEDIR_MS = 20_000L
        const val PAUSA_SISMOS_TRAS_PESTANA_MS = 8_000L
        const val INACTIVIDAD_SIGNOS_MS = 120_000L
        const val PREF_SONIDOS = "sonidos_activos"
        const val ID_VOZ_ALERTA = "alerta"
        const val ID_VOZ_RESULTADO = "resultado"
        const val ID_VOZ_SALUDO = "saludo"
        const val ESPERA_PARA_DORMIR_MS = 45_000L
        const val ESPERA_ENTRE_SALUDOS_MS = 60_000L
        const val ACCION_SIMULAR_ARDUINO = "com.example.detectormovimiento.SIMULAR_ARDUINO"
        const val PREF_MUJER = "respiracion_mujer"
        const val FRECUENCIA_MIN_PULSO_HZ = 40.0
        const val PERIODO_BIOMETRIA_US = 5_000 // 200 Hz
    }
}
