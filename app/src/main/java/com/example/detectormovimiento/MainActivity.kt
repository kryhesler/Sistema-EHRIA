package com.example.detectormovimiento

import android.Manifest
import android.content.ClipData
import android.content.BroadcastReceiver
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.ActivityInfo
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.content.res.ColorStateList
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
import android.graphics.Matrix
import android.graphics.Rect
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.hardware.usb.UsbManager
import android.media.AudioAttributes
import android.media.ExifInterface
import android.net.Uri
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
import android.view.WindowManager
import android.widget.Button
import android.widget.EditText
import android.widget.GridLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import androidx.activity.OnBackPressedCallback
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import com.example.detectormovimiento.AnalizadorVital.EstadoMedicion
import com.example.detectormovimiento.CaraRobotView.Expresion
import com.example.detectormovimiento.ComandosVoz.Comando
import com.example.detectormovimiento.DetectorSismico.Fase
import com.example.detectormovimiento.DetectorSismico.Nivel
import com.example.detectormovimiento.GuiasPrimerosAuxilios.Guia
import com.example.detectormovimiento.HistorialRepositorio.RegistroMedicion
import com.example.detectormovimiento.HistorialRepositorio.RegistroSismo
import com.example.detectormovimiento.HistorialRepositorio.TipoMedicion
import com.google.android.material.bottomnavigation.BottomNavigationView
import com.google.android.material.button.MaterialButton
import com.google.android.material.button.MaterialButtonToggleGroup
import com.google.android.material.materialswitch.MaterialSwitch
import com.google.android.material.progressindicator.LinearProgressIndicator
import java.io.File
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
    private var puertaAbierta = false
    private var ultimoModoLed: ProtocoloArduino.ModoLed? = null
    private val presencia = DetectorPresencia()
    private var robotDespierto = false
    private var ultimoSaludoMs = 0L
    // El parlante hace vibrar el teléfono: mientras suena (sirena, voz, efectos, metrónomo)
    // las muestras no se usan para detectar sismos. Hasta cuándo se considera que la voz suena.
    private var finVozMs = 0L
    private lateinit var layoutRobot: View
    private lateinit var caraRobot: CaraRobotView
    private lateinit var tvBurbuja: TextView
    // Primeros auxilios en modo robot
    private lateinit var columnaGuia: View
    private lateinit var columnaResultado: View
    private lateinit var grupoModoRobot: MaterialButtonToggleGroup
    private lateinit var grupoSexoRobot: MaterialButtonToggleGroup
    private lateinit var ivGuiaRobot: ImageView
    private lateinit var tvGuiaRobot: TextView
    private lateinit var tvValorRobot: TextView
    private lateinit var tvUnidadRobot: TextView
    private lateinit var tvEstadoValorRobot: TextView
    private lateinit var pbRobot: LinearProgressIndicator
    private lateinit var btnMedirRobot: MaterialButton
    /** Último valor mostrado de cada medición: (texto, estado, color). */
    private val valoresRobot = mutableMapOf<TipoMedicion, Triple<String, String, String>>()
    private var picoOndaVital = 0f
    // Señal con signo para el sismógrafo de la cara (desvío respecto del promedio lento).
    private var mediaSismoX = Float.NaN
    private var mediaSismoY = 0f
    private var mediaSismoZ = 0f
    private lateinit var barraRobot: View
    private lateinit var tvRobotEstado: TextView
    private lateinit var tvRobotInfo: TextView
    private lateinit var sismografoRobot: SismografoView
    private lateinit var btnRobotBotiquin: ImageView
    private lateinit var btnRobotHerramienta: View
    private var picoSismografoRobot = 0f
    private var ocultarSismografoPendiente = false
    // Subtítulos: lo que dice el robot se muestra frase por frase, al ritmo de la voz.
    private var textoBurbuja = ""
    private var iniciosFrases = listOf(0)
    private var indiceFrase = -1
    private var subtitulosSincronizados = false
    private var textoEnVoz = ""
    private lateinit var tarjetaSismograma: View
    private lateinit var scrollEstacion: View
    private lateinit var tvEstacionEstado: TextView
    private lateinit var tvEstacionTitulo: TextView
    private lateinit var tvEstacionDetalle: TextView
    private lateinit var circuloEstacion: View
    private lateinit var ivEstacion: ImageView
    private lateinit var tvEstTemp: TextView
    private lateinit var tvEstHum: TextView
    private lateinit var tvEstCalor: TextView
    private lateinit var tvEstCalorConsejo: TextView
    private lateinit var tvEstPresencia: TextView
    private lateinit var circuloBotiquin: View
    private lateinit var ivBotiquin: ImageView
    private lateinit var tvBotiquinEstado: TextView
    private lateinit var btnEstacionPuerta: Button
    // Interruptor de la pestaña Sismos. No se guarda: al abrir la app siempre vigila, por seguridad.
    private var deteccionActiva = true
    private var enPestanaSignos = false
    private var sismosEnPausa = false
    private lateinit var switchDeteccion: MaterialSwitch
    private lateinit var tvDetalleDeteccion: TextView

    // Asistente de voz de la estación: escucha "Ehria" y responde
    private lateinit var escucha: EscuchaVoz
    private var asistenteVozActivo = true
    private var enPrimerPlano = false
    private var microfonoPedido = false
    /** Hasta cuándo se acepta una orden sin decir "Ehria" (tras tocar el micrófono o decir solo el nombre). */
    private var esperandoOrdenHastaMs = 0L
    private var respuestaPendiente: Runnable? = null
    /** Se ejecuta cuando la voz termina la respuesta actual (en vez de volver a la cara de reposo). */
    private var accionTrasRespuesta: (() -> Unit)? = null
    private var ultimaRespuesta = ""
    private var vecesNoEntendi = 0
    private lateinit var btnRobotMicrofono: ImageView
    private lateinit var contenedorSubtitulos: View
    private lateinit var switchAsistenteVoz: MaterialSwitch
    private lateinit var tvVozEstado: TextView
    // Panel del asistente: guías paso a paso, metrónomo de la RCP, llamada simulada y ayuda
    private lateinit var panelRobot: View
    private lateinit var tvPanelEtiqueta: TextView
    private lateinit var tvPanelTitulo: TextView
    private lateinit var tvPanelTexto: TextView
    private lateinit var pbPanel: LinearProgressIndicator
    private lateinit var btnPanelAnterior: View
    private lateinit var btnPanelRepetir: View
    private lateinit var btnPanelSiguiente: View
    private var guiaActual: Guia? = null
    private var pasoGuia = 0
    private var metronomoActivo = false
    private var metronomoInicioMs = 0L
    private var llamadaActiva = false
    private var etapaLlamada = 0
    // Punto de encuentro: mapa durante la alarma y la evacuación
    private var puntoEncuentro: PuntoEncuentro? = null
    private var evacuacionHastaMs = 0L
    private lateinit var layoutMapa: View
    private lateinit var mapaEncuentro: MapaEncuentroView
    private lateinit var ivCroquis: ImageView
    private lateinit var tvMapaNombre: TextView
    private lateinit var tvMapaDireccion: TextView
    private lateinit var tvMapaIndicaciones: TextView
    private lateinit var tvEncuentroNombre: TextView
    private lateinit var tvEncuentroDetalle: TextView
    private lateinit var circuloEncuentro: View
    private lateinit var ivEncuentro: ImageView
    private var alElegirCroquis: ((Uri?) -> Unit)? = null

    private val pedirMicrofono = registerForActivityResult(ActivityResultContracts.RequestPermission()) { concedido ->
        if (!concedido) {
            Toast.makeText(this, "Sin el micrófono, el robot no puede escuchar \"Ehria\"", Toast.LENGTH_LONG).show()
        }
        actualizarEscucha()
    }

    private val elegirFoto = registerForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        alElegirCroquis?.invoke(uri)
    }

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
    /** Lo que la voz está diciendo ahora ("tipo#número"), o null si está callada. */
    private var vozActualId: String? = null
    private var contadorVoz = 0
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

    /**
     * La sirena se apaga sola tras [SIRENA_MAXIMA_MS] (la voz sigue repitiendo qué hacer): la
     * vibración del parlante puede mantener "viva" la alerta aunque el sismo ya haya terminado.
     */
    private val cortarSirenaRunnable = Runnable { sonidos.detenerSirena() }

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
            restablecerCaraTrasSismo()
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
        if (::scrollEstacion.isInitialized) {
            scrollEstacion.visibility = if (itemId == R.id.nav_estacion) View.VISIBLE else View.GONE
        }
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
            tvRobotEstado.compoundDrawableTintList = ColorStateList.valueOf(c)
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
        if (sismosEnPausa) return
        if (reanudarSismosEnMs > 0L) {
            if (SystemClock.elapsedRealtime() < reanudarSismosEnMs) return
            reanudarSismosEnMs = 0L
            detector.restablecer()
            ultimaFase = null
            ultimoAspectoEstado = null
        }
        // Mientras se simula un sismo (depuración), se ignora el sensor real.
        if (SystemClock.elapsedRealtime() < sismoSimuladoHastaMs) return
        // La sirena, la voz y los sonidos hacen vibrar el teléfono: eso no es un sismo.
        if (parlanteSonando()) {
            detector.omitir(SystemClock.elapsedRealtime())
            return
        }
        procesarMuestraSismica(event.values[0], event.values[1], event.values[2])
    }

    private fun procesarMuestraSismica(x: Float, y: Float, z: Float) {
        val total = sqrt(x * x + y * y + z * z)
        // Sin sensor de aceleración lineal, se resta la gravedad a la magnitud.
        val magnitud = if (isLinearAvailable) total else abs(total - SensorManager.GRAVITY_EARTH)

        val ahora = SystemClock.elapsedRealtime()
        val estado = detector.procesar(magnitud, ahora)

        sismografoView.agregarPunto(estado.pga)
        alimentarSismografoRobot(estado, x, y, z)
        registrarSismo(estado)

        if (estado.alertaNueva) {
            ultimoNivel = estado.nivel
            // Ante un sismo se dejan de lado las guías y la llamada: primero, protegerse.
            cerrarExtrasRobot()
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
        if (estado.fase == Fase.EVALUANDO && ultimaFase == Fase.NORMAL) mostrarSismograma()
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
                handler.postDelayed({ hablarAlerta(mensajeFinSismo()) }, 900L)
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
        if (::sismografoRobot.isInitialized) sismografoRobot.setStrokeColor(color)
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
        handler.removeCallbacks(cortarSirenaRunnable)
        handler.postDelayed(cortarSirenaRunnable, SIRENA_MAXIMA_MS)
        actualizarEscucha()
    }

    private fun detenerAlarma() {
        alarmaSonando = false
        handler.removeCallbacks(alarmaRunnable)
        handler.removeCallbacks(cortarSirenaRunnable)
        sonidos.detenerSirena()
        actualizarEscucha()
    }

    /** Lo que dice la estación cuando termina la sacudida (con el punto de encuentro, si hay). */
    private fun mensajeFinSismo(): String {
        val punto = puntoEncuentro ?: return "La sacudida terminó. Revisa si hay personas heridas."
        return "La sacudida terminó. Si hay que evacuar, ve con calma al punto de encuentro: ${punto.nombre}, " +
            "${punto.ubicacion().replaceFirstChar { it.lowercase() }}. Revisa si hay personas heridas."
    }

    // ===================== ESTACIÓN: ARDUINO Y MODO ROBOT =====================

    private fun iniciarEstacion() {
        layoutRobot = findViewById(R.id.layoutRobot)
        caraRobot = findViewById(R.id.caraRobot)
        tvBurbuja = findViewById(R.id.tvBurbuja)
        columnaGuia = findViewById(R.id.columnaGuia)
        columnaResultado = findViewById(R.id.columnaResultado)
        grupoModoRobot = findViewById(R.id.grupoModoRobot)
        grupoSexoRobot = findViewById(R.id.grupoSexoRobot)
        ivGuiaRobot = findViewById(R.id.ivGuiaRobot)
        tvGuiaRobot = findViewById(R.id.tvGuiaRobot)
        tvValorRobot = findViewById(R.id.tvValorRobot)
        tvUnidadRobot = findViewById(R.id.tvUnidadRobot)
        tvEstadoValorRobot = findViewById(R.id.tvEstadoValorRobot)
        pbRobot = findViewById(R.id.pbRobot)
        btnMedirRobot = findViewById(R.id.btnMedirRobot)
        barraRobot = findViewById(R.id.barraRobot)
        tvRobotEstado = findViewById(R.id.tvRobotEstado)
        tvRobotInfo = findViewById(R.id.tvRobotInfo)
        sismografoRobot = findViewById(R.id.sismografoRobot)
        btnRobotBotiquin = findViewById(R.id.btnRobotBotiquin)
        btnRobotHerramienta = findViewById(R.id.btnRobotHerramienta)
        tarjetaSismograma = findViewById(R.id.tarjetaSismograma)
        sismografoRobot.cuadricula = false
        sismografoRobot.setGrosorLinea(2.5f)
        sismografoRobot.zonaDesvanecida = { caraRobot.limitesHorizontales() }
        sismografoRobot.centroLinea = { caraRobot.centroVertical() }
        sismografoRobot.alturaOnda = 170f * resources.displayMetrics.density

        grupoModoRobot.addOnButtonCheckedListener { _, id, marcado ->
            if (marcado) grupoPanel.check(if (id == R.id.btnRobotPulso) R.id.btnPanelPulso else R.id.btnPanelRespiracion)
        }
        grupoSexoRobot.addOnButtonCheckedListener { _, id, marcado ->
            if (marcado) grupoSexo.check(if (id == R.id.btnRobotMujer) R.id.btnMujer else R.id.btnHombre)
        }
        btnMedirRobot.setOnClickListener { alTocarMedir(panelSignos) }
        grupoModoRobot.check(if (panelSignos == TipoMedicion.PULSO) R.id.btnRobotPulso else R.id.btnRobotRespiracion)
        grupoSexoRobot.check(if (esMujer) R.id.btnRobotMujer else R.id.btnRobotHombre)
        actualizarGuiaRobot()
        mostrarResultadoRobot()
        scrollEstacion = findViewById(R.id.scrollEstacion)
        tvEstacionEstado = findViewById(R.id.tvEstacionEstado)
        tvEstacionTitulo = findViewById(R.id.tvEstacionTitulo)
        tvEstacionDetalle = findViewById(R.id.tvEstacionDetalle)
        circuloEstacion = findViewById(R.id.circuloEstacion)
        ivEstacion = findViewById(R.id.ivEstacion)
        tvEstTemp = findViewById(R.id.tvEstTemp)
        tvEstHum = findViewById(R.id.tvEstHum)
        tvEstCalor = findViewById(R.id.tvEstCalor)
        tvEstCalorConsejo = findViewById(R.id.tvEstCalorConsejo)
        tvEstPresencia = findViewById(R.id.tvEstPresencia)
        circuloBotiquin = findViewById(R.id.circuloBotiquin)
        ivBotiquin = findViewById(R.id.ivBotiquin)
        tvBotiquinEstado = findViewById(R.id.tvBotiquinEstado)
        btnEstacionPuerta = findViewById(R.id.btnEstacionPuerta)

        btnEstacionPuerta.setOnClickListener { abrirBotiquin(!puertaAbierta) }
        btnRobotBotiquin.setOnClickListener { abrirBotiquin(!puertaAbierta) }
        iniciarAsistente()
        findViewById<Button>(R.id.btnModoRobot).setOnClickListener { entrarModoEstacion() }
        findViewById<Button>(R.id.btnModoHerramienta).setOnClickListener {
            entrarModoHerramienta("Primeros auxilios. Elige respiración o pulso y te guío paso a paso.")
        }
        btnRobotHerramienta.setOnClickListener {
            entrarModoHerramienta("Primeros auxilios. Elige respiración o pulso y te guío paso a paso.")
        }
        findViewById<View>(R.id.btnSalirRobot).setOnClickListener { atrasEnModoRobot() }
        caraRobot.setOnClickListener {
            // Si no toca saludar (saludó hace poco), tocar la cara es como tocar el micrófono.
            if (modoRobot == ModoRobot.ESTACION && !despertar(saludar = true) && asistenteVozActivo) escucharOrden()
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
        // App de prueba: también acepta el Arduino conectado a la computadora (ehria.bat puente).
        if (applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE != 0) arduino.puertoPuente = PUERTO_PUENTE
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
            // El Uno se reinicia al abrir el puerto (y cierra la puerta): cuando ya arrancó,
            // se le repite cómo debe estar todo.
            handler.removeCallbacks(reenviarEstadoArduino)
            handler.postDelayed(reenviarEstadoArduino, 2_500L)
            // La cabeza volvió al cuerpo: siempre modo estación. El medidor solo se usa fuera de ella.
            handler.removeCallbacks(herramientaTrasDesconexion)
            if (medicionActiva) cancelarMedicion("La estación se conectó: medición detenida.")
            if (modoRobot != ModoRobot.ESTACION) entrarModoEstacion()
        } else {
            ultimaLectura = null
            presencia.reiniciar()
            actualizarPanelEstacion()
            // Se desconectó el cable: sacaron la cabeza. Se espera un momento por si fue solo
            // un falso contacto al acomodar el teléfono en la carcasa.
            if (modoRobot == ModoRobot.ESTACION) {
                handler.removeCallbacks(herramientaTrasDesconexion)
                handler.postDelayed(herramientaTrasDesconexion, ESPERA_DESCONEXION_MS)
            }
        }
    }

    private val reenviarEstadoArduino = Runnable {
        if (arduino.conectado) {
            ultimoModoLed = null
            actualizarLedExterior()
            arduino.enviar(ProtocoloArduino.comandoLuzInterior(puertaAbierta))
            if (puertaAbierta) arduino.enviar(ProtocoloArduino.comandoPuerta(true))
        }
    }

    private val herramientaTrasDesconexion = Runnable {
        // Solo si de verdad sacaron el teléfono. Con el cable puesto fue un corte de la
        // comunicación (por ejemplo, un bajón al mover el servo): se reconecta sola y el
        // teléfono sigue en la pared, así que no se abre el medidor.
        if (!arduino.conectado && !arduino.cablePuesto() && modoRobot == ModoRobot.ESTACION) {
            entrarModoHerramienta("Ya estoy fuera de la estación. Elige respiración o pulso y te guío paso a paso.")
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
            Toast.makeText(this, "Conecta la estación para mover la puerta del botiquín", Toast.LENGTH_SHORT).show()
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
        btnEstacionPuerta.text = if (puertaAbierta) "Cerrar" else "Abrir"
        val color = Color.parseColor(if (puertaAbierta) "#22C55E" else "#94A3B8")
        tvBotiquinEstado.text = if (puertaAbierta) "Abierto" else "Cerrado"
        ivBotiquin.imageTintList = ColorStateList.valueOf(color)
        circuloBotiquin.backgroundTintList = ColorStateList.valueOf(conAlfa(color, 0x26))
        btnRobotBotiquin.contentDescription = texto
        btnRobotBotiquin.imageTintList = ColorStateList.valueOf(
            Color.parseColor(if (puertaAbierta) "#22C55E" else "#475569")
        )
        actualizarPanelEstacion()
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
        val conectada = arduino.conectado
        // El medidor solo está disponible fuera de la estación.
        val medidor = if (conectada) View.GONE else View.VISIBLE
        btnRobotHerramienta.visibility = medidor
        findViewById<View>(R.id.btnModoHerramienta).visibility = medidor
        val color = Color.parseColor(if (conectada) "#22C55E" else "#94A3B8")
        tvEstacionEstado.text = if (conectada) "CONECTADA" else "SIN CONECTAR"
        tvEstacionEstado.setTextColor(color)
        ivEstacion.imageTintList = ColorStateList.valueOf(color)
        circuloEstacion.backgroundTintList = ColorStateList.valueOf(conAlfa(color, 0x26))
        if (conectada) {
            tvEstacionTitulo.text = "La estación está lista"
            tvEstacionDetalle.text = "Saluda a quien se acerca, mide el ambiente y abre el botiquín ante un sismo."
        } else {
            tvEstacionTitulo.text = "Coloca el teléfono en la estación"
            tvEstacionDetalle.text = "Conéctalo a la base con el cable USB. La cara del robot se activa sola."
        }
        actualizarDatosEstacion()
    }

    private fun actualizarDatosEstacion() {
        val l = ultimaLectura
        val t = l?.temperaturaC
        val h = l?.humedad
        tvEstTemp.text = t?.let { String.format(Locale.getDefault(), "%.1f°", it) } ?: "--"
        tvEstHum.text = h?.let { "${it.roundToInt()} %" } ?: "--"
        if (t != null && h != null) {
            val indice = AsistenteEstacion.indiceCalor(t, h)
            val riesgo = AsistenteEstacion.riesgoCalor(indice)
            tvEstCalor.text = "${indice.roundToInt()}°"
            tvEstCalor.setTextColor(
                Color.parseColor(
                    when {
                        indice >= 41 -> "#F87171"
                        indice >= 32 -> "#FBBF24"
                        else -> "#F8FAFC"
                    }
                )
            )
            tvEstCalorConsejo.visibility = if (riesgo != null) View.VISIBLE else View.GONE
            tvEstCalorConsejo.text = riesgo?.let {
                "Calor: ${it}. Recomienda tomar agua y descansar a la sombra."
            }.orEmpty()
        } else {
            tvEstCalor.text = "--"
            tvEstCalor.setTextColor(Color.parseColor("#F8FAFC"))
            tvEstCalorConsejo.visibility = View.GONE
        }
        val distancia = l?.distanciaCm
        tvEstPresencia.text = when {
            !arduino.conectado -> "Sin datos de presencia"
            distancia != null && distancia < 160f -> "Hay alguien a ${distancia.roundToInt()} cm"
            else -> "Nadie cerca de la estación"
        }
        actualizarPanelEstacion()
    }

    /** Una sola línea discreta bajo la cara: clima, sismos de hoy y el botiquín si está abierto. */
    private fun actualizarPanelEstacion() {
        if (!::tvRobotInfo.isInitialized || modoRobot == ModoRobot.NINGUNO) return
        val l = ultimaLectura
        val t = l?.temperaturaC
        val h = l?.humedad
        val partes = mutableListOf<String>()
        if (t != null) {
            var temperatura = "${t.roundToInt()}°"
            if (h != null) {
                val indice = AsistenteEstacion.indiceCalor(t, h).roundToInt()
                if (indice >= 27 && indice > t.roundToInt()) temperatura += " (se siente $indice°)"
            }
            partes += temperatura
        }
        if (h != null) partes += "${h.roundToInt()} % humedad"
        if (t == null && h == null && !arduino.conectado) partes += "Sin estación"
        val hoy = sismosDeHoy()
        partes += when {
            !deteccionActiva -> "Detección apagada"
            hoy.isEmpty() -> "Sin sismos hoy"
            else -> {
                val m = EscalaSismica.magnitudEstimada(hoy.maxBy { it.fechaMs }.pgaPicoBruto)
                val ultimo = m?.let { String.format(Locale.getDefault(), " (último ≈ %.1f)", it) }.orEmpty()
                "${hoy.size} ${if (hoy.size == 1) "sismo" else "sismos"} hoy$ultimo"
            }
        }
        if (puertaAbierta) partes += "Botiquín abierto"
        if (::escucha.isInitialized && escucha.estado == EscuchaVoz.Estado.ESCUCHANDO) partes += "Di «Ehria»"
        tvRobotInfo.text = partes.joinToString("   ·   ")
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
        modoRobot = ModoRobot.ESTACION
        if (navInferior.selectedItemId != R.id.nav_sismos) navInferior.selectedItemId = R.id.nav_sismos
        mostrarCapaRobot(true)
        mostrarHerramientaRobot(false)
        ocultarSismografoRobot()
        presencia.reiniciar()
        actualizarPausaSismos(graciaMs = PAUSA_SISMOS_TRAS_PESTANA_MS)
        actualizarPanelEstacion()
        dormir()
        // El micrófono se pide una sola vez, al poner la cara del robot.
        if (asistenteVozActivo && !permisoMicrofono() && !microfonoPedido) {
            microfonoPedido = true
            pedirMicrofono.launch(Manifest.permission.RECORD_AUDIO)
        }
        actualizarEscucha()
    }

    /** Cabeza fuera del cuerpo: signos vitales en horizontal, con la cara del robot. */
    private fun entrarModoHerramienta(anuncio: String) {
        // Con la estación conectada el teléfono está en la pared: no se mide a nadie.
        if (arduino.conectado) return
        cerrarExtrasRobot()
        terminarEvacuacion()
        modoRobot = ModoRobot.HERRAMIENTA
        mostrarCapaRobot(true)
        mostrarHerramientaRobot(true)
        ocultarSismografoRobot()
        mostrarAdornosRobot(true)
        actualizarPausaSismos(graciaMs = 0L)
        handler.removeCallbacks(dormirRunnable)
        robotDespierto = true
        brilloPantalla(1f)
        caraRobot.expresion = CaraRobotView.Expresion.DESPIERTO
        decirEnBurbuja(anuncio)
        hablar(anuncio)
        actualizarEscucha()
    }

    private fun salirModoRobot() {
        if (medicionActiva) cancelarMedicion("Medición detenida.")
        cerrarExtrasRobot()
        terminarEvacuacion()
        modoRobot = ModoRobot.NINGUNO
        actualizarEscucha()
        handler.removeCallbacks(dormirRunnable)
        mostrarCapaRobot(false)
        decirEnBurbuja("")
        handler.removeCallbacks(ocultarSismografoRunnable)
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

    /** Estación: datos y accesos. Primeros auxilios: guía a la izquierda y resultado a la derecha. */
    private fun mostrarHerramientaRobot(herramienta: Boolean) {
        val enHerramienta = if (herramienta) View.VISIBLE else View.GONE
        val enEstacion = if (herramienta) View.GONE else View.VISIBLE
        columnaGuia.visibility = enHerramienta
        columnaResultado.visibility = enHerramienta
        grupoModoRobot.visibility = enHerramienta
        barraRobot.visibility = enEstacion
        tvRobotInfo.visibility = enEstacion
        if (herramienta) {
            actualizarGuiaRobot()
            mostrarResultadoRobot()
            actualizarBotonesMedicion()
        }
    }

    /** La ilustración y el texto de dónde colocar el teléfono, según la medición y el sexo. */
    private fun actualizarGuiaRobot() {
        if (!::ivGuiaRobot.isInitialized) return
        val respiracion = panelSignos == TipoMedicion.RESPIRACION
        ivGuiaRobot.setImageResource(
            when {
                !respiracion -> R.drawable.ilustracion_pulso
                esMujer -> R.drawable.ilustracion_respiracion_mujer
                else -> R.drawable.ilustracion_respiracion_hombre
            }
        )
        tvGuiaRobot.text = when {
            !respiracion -> "Lado izquierdo del pecho, boca arriba"
            esMujer -> "Centro del pecho, boca arriba"
            else -> "Sobre el abdomen, boca arriba"
        }
        grupoSexoRobot.visibility = if (respiracion) View.VISIBLE else View.GONE
    }

    /** Muestra a la derecha el último valor de la medición elegida, en grande. */
    private fun mostrarResultadoRobot() {
        if (!::tvValorRobot.isInitialized) return
        val respiracion = panelSignos == TipoMedicion.RESPIRACION
        val (texto, estado, color) = valoresRobot[panelSignos] ?: Triple("--", "Listo para medir", "#94A3B8")
        val corte = texto.lastIndexOf(' ')
        tvValorRobot.text = if (corte > 0) texto.substring(0, corte) else texto
        tvValorRobot.setTextColor(if (texto.startsWith("--")) Color.parseColor("#475569") else Color.parseColor(color))
        tvUnidadRobot.text = if (respiracion) "respiraciones por minuto" else "latidos por minuto"
        tvEstadoValorRobot.text = estado
        tvEstadoValorRobot.setTextColor(Color.parseColor(color))
    }


    // ---------- Despertar, saludar y dormir ----------

    /** Devuelve true si además saludó. */
    private fun despertar(saludar: Boolean): Boolean {
        robotDespierto = true
        brilloPantalla(1f)
        mostrarAdornosRobot(true)
        if (caraRobot.expresion == CaraRobotView.Expresion.DORMIDO) caraRobot.expresion = CaraRobotView.Expresion.DESPIERTO
        programarSueno()
        actualizarEscucha()
        val ahora = SystemClock.elapsedRealtime()
        if (saludar && modoRobot == ModoRobot.ESTACION && ultimaFase != Fase.ALERTA && !asistenteOcupado() &&
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
                    asistenteVoz = asistenteVozActivo && permisoMicrofono(),
                )
            )
            decirEnBurbuja(texto)
            caraRobot.expresion = CaraRobotView.Expresion.FELIZ
            handler.postDelayed({
                if (caraRobot.expresion == CaraRobotView.Expresion.FELIZ) caraRobot.expresion = CaraRobotView.Expresion.DESPIERTO
            }, 1_500L)
            hablar(texto, ID_VOZ_SALUDO)
            return true
        }
        return false
    }

    private fun dormir() {
        if (modoRobot != ModoRobot.ESTACION || ultimaFase == Fase.ALERTA || presencia.presente) return
        if (sismografoRobot.visibility == View.VISIBLE || asistenteOcupado() || evacuando()) return
        robotDespierto = false
        caraRobot.expresion = CaraRobotView.Expresion.DORMIDO
        decirEnBurbuja("")
        mostrarAdornosRobot(false)
        brilloPantalla(0.04f)
        actualizarEscucha()
    }

    /** Mientras duerme solo se ve la cara; al despertar aparecen la línea de datos y los accesos. */
    private fun mostrarAdornosRobot(visibles: Boolean) {
        val alfa = if (visibles) 1f else 0f
        barraRobot.animate().alpha(alfa).setDuration(400L).start()
        tvRobotInfo.animate().alpha(alfa).setDuration(400L).start()
    }

    // ---------- Subtítulos ----------

    /** Muestra el texto frase por frase. Si la voz informa por dónde va, se sincroniza con ella. */
    private fun decirEnBurbuja(texto: String) {
        handler.removeCallbacks(avanzarSubtitulo)
        textoBurbuja = texto.trim()
        iniciosFrases = listOf(0) + Regex("(?<=[.!?])\\s+").findAll(textoBurbuja).map { it.range.last + 1 }.toList()
        indiceFrase = -1
        subtitulosSincronizados = false
        mostrarFrase(0)
        if (iniciosFrases.size > 1) handler.postDelayed(avanzarSubtitulo, duracionFrase(0))
    }

    private fun frase(i: Int): String =
        textoBurbuja.substring(iniciosFrases[i], iniciosFrases.getOrNull(i + 1) ?: textoBurbuja.length).trim()

    /** Lo que tarda la voz en decir una frase, aproximadamente. */
    private fun duracionFrase(i: Int): Long = 700L + frase(i).length * 68L

    private fun mostrarFrase(i: Int) {
        if (i == indiceFrase || i !in iniciosFrases.indices) return
        indiceFrase = i
        val texto = frase(i)
        tvBurbuja.text = texto
        tvBurbuja.visibility = if (texto.isEmpty()) View.GONE else View.VISIBLE
    }

    private val avanzarSubtitulo = object : Runnable {
        override fun run() {
            if (subtitulosSincronizados) return
            val siguiente = indiceFrase + 1
            if (siguiente >= iniciosFrases.size) return
            mostrarFrase(siguiente)
            handler.postDelayed(this, duracionFrase(siguiente))
        }
    }

    /** Llamado por la voz con la posición de la palabra que está diciendo. */
    private fun alAvanzarVoz(inicio: Int) {
        if (textoEnVoz.trim() != textoBurbuja || textoBurbuja.isEmpty()) return
        subtitulosSincronizados = true
        handler.removeCallbacks(avanzarSubtitulo)
        val desfase = textoEnVoz.length - textoEnVoz.trimStart().length
        mostrarFrase(iniciosFrases.indexOfLast { it <= inicio - desfase }.coerceAtLeast(0))
    }

    // ---------- Sismógrafo en la cara ----------

    /** En la estación, el sismógrafo aparece bajo la cara en cuanto el suelo empieza a moverse. */
    private fun alimentarSismografoRobot(estado: DetectorSismico.Estado, x: Float, y: Float, z: Float) {
        if (modoRobot != ModoRobot.ESTACION) return
        // La onda sube y baja como un sismograma: desvío de cada eje respecto de su promedio lento.
        if (mediaSismoX.isNaN()) {
            mediaSismoX = x; mediaSismoY = y; mediaSismoZ = z
        }
        mediaSismoX += 0.02f * (x - mediaSismoX)
        mediaSismoY += 0.02f * (y - mediaSismoY)
        mediaSismoZ += 0.02f * (z - mediaSismoZ)
        val senal = (x - mediaSismoX) + (y - mediaSismoY) + (z - mediaSismoZ)
        // Escala automática: se ven bien tanto un temblor pequeño como uno fuerte.
        picoSismografoRobot = maxOf(picoSismografoRobot * 0.995f, abs(senal))
        sismografoRobot.rango = maxOf(0.2f, picoSismografoRobot * 1.2f)
        sismografoRobot.agregarPunto(senal)
        val fluctua = estado.fase == Fase.EVALUANDO || estado.fase == Fase.ALERTA
        if (fluctua) {
            if (ocultarSismografoPendiente) {
                handler.removeCallbacks(ocultarSismografoRunnable)
                ocultarSismografoPendiente = false
            }
            if (sismografoRobot.visibility != View.VISIBLE) {
                sismografoRobot.bordeDesvanecido = 0.1f
                sismografoRobot.visibility = View.VISIBLE
                despertar(saludar = false)
                handler.removeCallbacks(dormirRunnable)
                if (estado.fase == Fase.EVALUANDO) {
                    caraRobot.expresion = CaraRobotView.Expresion.ATENTO
                    decirEnBurbuja("Percibo un movimiento… lo estoy verificando.")
                }
            }
        } else if (sismografoRobot.visibility == View.VISIBLE && !ocultarSismografoPendiente) {
            ocultarSismografoPendiente = true
            handler.postDelayed(ocultarSismografoRunnable, 6_000L)
        }
    }

    private val ocultarSismografoRunnable = Runnable {
        ocultarSismografoRobot()
        if (modoRobot == ModoRobot.ESTACION && caraRobot.expresion == CaraRobotView.Expresion.ATENTO) {
            caraRobot.expresion = CaraRobotView.Expresion.DESPIERTO
            decirEnBurbuja("")
        }
        programarSueno()
    }

    private fun ocultarSismografoRobot() {
        handler.removeCallbacks(ocultarSismografoRunnable)
        ocultarSismografoPendiente = false
        sismografoRobot.visibility = View.GONE
        sismografoRobot.limpiar()
        picoSismografoRobot = 0f
    }

    /** Fuera del modo robot: al empezar a moverse, se muestra el sismograma en la pestaña Sismos. */
    private fun mostrarSismograma() {
        if (modoRobot != ModoRobot.NINGUNO) return
        if (navInferior.selectedItemId != R.id.nav_sismos) navInferior.selectedItemId = R.id.nav_sismos
        val scroll = scrollSismos as ScrollView
        scroll.post {
            val margen = (12 * resources.displayMetrics.density).toInt()
            val visibleArriba = scroll.scrollY
            val visibleAbajo = scroll.scrollY + scroll.height
            if (tarjetaSismograma.top < visibleArriba || tarjetaSismograma.bottom > visibleAbajo) {
                scroll.smoothScrollTo(0, (tarjetaSismograma.top - margen).coerceAtLeast(0))
            }
        }
    }

    private val dormirRunnable = Runnable { dormir() }

    private fun programarSueno() {
        handler.removeCallbacks(dormirRunnable)
        if (modoRobot == ModoRobot.ESTACION) handler.postDelayed(dormirRunnable, ESPERA_PARA_DORMIR_MS)
    }

    /**
     * Ante un sismo: el robot despierta, abre el botiquín y enciende la luz interior. Cada
     * tanto, la pantalla muestra por un momento el mapa del punto de encuentro.
     */
    private fun alEmpezarSismoEnEstacion() {
        handler.removeCallbacks(finEvacuacionRunnable)
        handler.removeCallbacks(caraTrasSismoRunnable)
        evacuacionHastaMs = 0L
        if (modoRobot == ModoRobot.ESTACION) {
            robotDespierto = true
            brilloPantalla(1f)
            handler.removeCallbacks(dormirRunnable)
            mostrarAdornosRobot(true)
            decirEnBurbuja("¡Sismo! Agáchate, cúbrete y sujétate.")
            handler.removeCallbacks(mapaDuranteSismoRunnable)
            handler.postDelayed(mapaDuranteSismoRunnable, PRIMER_MAPA_EN_ALARMA_MS)
        }
        caraRobot.expresion = CaraRobotView.Expresion.ALERTA
        if (arduino.conectado && !puertaAbierta) abrirBotiquin(true)
        actualizarLedExterior()
        actualizarPanelEstacion()
    }

    /**
     * Terminó la sacudida: empieza la evacuación. La cara queda preocupada un momento y vuelve
     * sola a la normalidad; el mapa del punto de encuentro sigue apareciendo cada tanto hasta
     * que termina la evacuación. Nadie tiene que tocar la pantalla.
     */
    private fun alTerminarSismoEnEstacion() {
        caraRobot.expresion = CaraRobotView.Expresion.PREOCUPADO
        decirEnBurbuja(mensajeFinSismo())
        handler.removeCallbacks(caraTrasSismoRunnable)
        handler.postDelayed(caraTrasSismoRunnable, CARA_PREOCUPADA_MS)
        actualizarPanelEstacion()
        evacuacionHastaMs = SystemClock.elapsedRealtime() + if (puntoEncuentro != null) EVACUACION_MS else ESPERA_TRAS_SISMO_MS
        handler.removeCallbacks(finEvacuacionRunnable)
        handler.postDelayed(finEvacuacionRunnable, evacuacionHastaMs - SystemClock.elapsedRealtime())
        handler.removeCallbacks(mapaDuranteSismoRunnable)
        if (modoRobot == ModoRobot.ESTACION && puntoEncuentro != null) {
            handler.postDelayed({
                if (evacuando()) {
                    mostrarMapa(MAPA_AL_TERMINAR_MS)
                    handler.postDelayed(mapaDuranteSismoRunnable, MAPA_AL_TERMINAR_MS + CARA_ENTRE_MAPAS_MS)
                }
            }, 900L)
        }
    }

    private fun evacuando() = evacuacionHastaMs > 0L && SystemClock.elapsedRealtime() < evacuacionHastaMs

    /** Durante la alarma y la evacuación, el mapa aparece unos segundos y vuelve la cara. */
    private val mapaDuranteSismoRunnable = object : Runnable {
        override fun run() {
            if (modoRobot != ModoRobot.ESTACION || puntoEncuentro == null) return
            val enAlerta = ultimaFase == Fase.ALERTA
            if (!enAlerta && !evacuando()) return
            if (!asistenteOcupado()) {
                mostrarMapa(if (enAlerta) MAPA_EN_ALARMA_MS else MAPA_EN_EVACUACION_MS)
            }
            handler.postDelayed(this, if (enAlerta) MAPA_EN_ALARMA_MS + CARA_ENTRE_MAPAS_MS else MAPA_EN_EVACUACION_MS + CARA_ENTRE_MAPAS_MS)
        }
    }

    private val finEvacuacionRunnable = Runnable { terminarEvacuacion() }

    private val caraTrasSismoRunnable = Runnable {
        if (ultimaFase == Fase.ALERTA) return@Runnable
        if (caraRobot.expresion == Expresion.PREOCUPADO) caraRobot.expresion = expresionReposo()
        if (textoBurbuja == mensajeFinSismo().trim()) decirEnBurbuja("")
        programarSueno()
    }

    /** Vuelve a la cara de siempre: sin mapa, sin subtítulo y con el sueño programado. */
    private fun terminarEvacuacion() {
        val estaba = evacuacionHastaMs > 0L
        evacuacionHastaMs = 0L
        handler.removeCallbacks(finEvacuacionRunnable)
        handler.removeCallbacks(mapaDuranteSismoRunnable)
        if (!estaba || ultimaFase == Fase.ALERTA) return
        ocultarMapa()
        if (modoRobot != ModoRobot.ESTACION) return
        if (!asistenteOcupado()) {
            caraRobot.expresion = expresionReposo()
            decirEnBurbuja("")
        }
        programarSueno()
    }

    // ===================== ASISTENTE DE VOZ =====================

    private fun iniciarAsistente() {
        btnRobotMicrofono = findViewById(R.id.btnRobotMicrofono)
        contenedorSubtitulos = findViewById(R.id.contenedorSubtitulos)
        switchAsistenteVoz = findViewById(R.id.switchAsistenteVoz)
        tvVozEstado = findViewById(R.id.tvVozEstado)
        panelRobot = findViewById(R.id.panelRobot)
        tvPanelEtiqueta = findViewById(R.id.tvPanelEtiqueta)
        tvPanelTitulo = findViewById(R.id.tvPanelTitulo)
        tvPanelTexto = findViewById(R.id.tvPanelTexto)
        pbPanel = findViewById(R.id.pbPanel)
        btnPanelAnterior = findViewById(R.id.btnPanelAnterior)
        btnPanelRepetir = findViewById(R.id.btnPanelRepetir)
        btnPanelSiguiente = findViewById(R.id.btnPanelSiguiente)
        layoutMapa = findViewById(R.id.layoutMapa)
        mapaEncuentro = findViewById(R.id.mapaEncuentro)
        ivCroquis = findViewById(R.id.ivCroquis)
        tvMapaNombre = findViewById(R.id.tvMapaNombre)
        tvMapaDireccion = findViewById(R.id.tvMapaDireccion)
        tvMapaIndicaciones = findViewById(R.id.tvMapaIndicaciones)
        tvEncuentroNombre = findViewById(R.id.tvEncuentroNombre)
        tvEncuentroDetalle = findViewById(R.id.tvEncuentroDetalle)
        circuloEncuentro = findViewById(R.id.circuloEncuentro)
        ivEncuentro = findViewById(R.id.ivEncuentro)

        findViewById<TextView>(R.id.tvVozEjemplos).text = AsistenteEstacion.EJEMPLOS.joinToString("\n") { "«$it»" }
        asistenteVozActivo = getPreferences(MODE_PRIVATE).getBoolean(PREF_ASISTENTE_VOZ, true)
        switchAsistenteVoz.isChecked = asistenteVozActivo
        switchAsistenteVoz.setOnCheckedChangeListener { _, activo ->
            asistenteVozActivo = activo
            getPreferences(MODE_PRIVATE).edit().putBoolean(PREF_ASISTENTE_VOZ, activo).apply()
            if (activo && !permisoMicrofono()) pedirMicrofono.launch(Manifest.permission.RECORD_AUDIO)
            actualizarEscucha()
        }
        btnRobotMicrofono.setOnClickListener { escucharOrden() }
        btnPanelAnterior.setOnClickListener { moverGuia(-1) }
        btnPanelRepetir.setOnClickListener { moverGuia(0) }
        btnPanelSiguiente.setOnClickListener { moverGuia(1) }
        findViewById<View>(R.id.btnPanelCerrar).setOnClickListener { detenerAsistente(hablando = false) }
        layoutMapa.setOnClickListener { ocultarMapa() }

        escucha = EscuchaVoz(
            this,
            alOirParcial = ::alOirParcial,
            alOirFrase = ::alOirFrase,
            alCambiarNivel = { caraRobot.nivelVoz = it },
            alCambiarEstado = { actualizarEstadoVoz() },
        )

        puntoEncuentro = PuntoEncuentro.cargar(getPreferences(MODE_PRIVATE))
        findViewById<Button>(R.id.btnConfigurarEncuentro).setOnClickListener { configurarPuntoEncuentro() }
        findViewById<Button>(R.id.btnVerMapa).setOnClickListener { verMapaPrevia() }
        actualizarTarjetaEncuentro()
        actualizarEstadoVoz()
    }

    private fun permisoMicrofono() =
        ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED

    /**
     * El robot escucha solo en la estación, despierto (alguien se acercó o tocaron la cara) y
     * callado: mientras habla no escucha, para no oírse a sí mismo. Durante la sirena tampoco.
     */
    private fun actualizarEscucha() {
        if (!::escucha.isInitialized) return
        val escuchar = asistenteVozActivo && permisoMicrofono() && enPrimerPlano &&
            modoRobot == ModoRobot.ESTACION && robotDespierto && vozActualId == null &&
            !alarmaSonando && !medicionActiva
        if (escuchar) escucha.iniciar() else escucha.detener()
        actualizarEstadoVoz()
    }

    private val reanudarEscuchaRunnable = Runnable { actualizarEscucha() }

    private fun actualizarEstadoVoz() {
        if (!::tvVozEstado.isInitialized) return
        val estado = if (::escucha.isInitialized) escucha.estado else EscuchaVoz.Estado.APAGADA
        tvVozEstado.text = when {
            !asistenteVozActivo -> "Apagado: el robot no escucha."
            !permisoMicrofono() -> "Falta el permiso del micrófono. Apaga y enciende el interruptor para darlo."
            estado == EscuchaVoz.Estado.NO_DISPONIBLE ->
                "Este teléfono no tiene reconocimiento de voz. Activa la app de Google para usarlo."
            estado == EscuchaVoz.Estado.SIN_CONEXION ->
                "Sin internet: descarga el español para la voz sin conexión (Ajustes de Google › Voz)."
            estado == EscuchaVoz.Estado.ESCUCHANDO -> "Escuchando: di «Ehria» y lo que necesitas."
            else -> "Escucha cuando el robot está despierto: alguien se acerca o tocas su cara."
        }
        val escuchando = asistenteVozActivo && estado == EscuchaVoz.Estado.ESCUCHANDO
        btnRobotMicrofono.visibility = if (asistenteVozActivo) View.VISIBLE else View.GONE
        btnRobotMicrofono.imageTintList = ColorStateList.valueOf(Color.parseColor(if (escuchando) "#22D3EE" else "#475569"))
        actualizarPanelEstacion()
    }

    /** Mientras alguien habla: si ya dijo "Ehria", la cara muestra que está escuchando. */
    private fun alOirParcial(texto: String) {
        if (modoRobot != ModoRobot.ESTACION || metronomoActivo) return
        if (caraRobot.expresion != Expresion.ESCUCHANDO && ComandosVoz.escuchar(texto).llamado) {
            caraRobot.expresion = Expresion.ESCUCHANDO
            programarSueno()
        }
    }

    private fun alOirFrase(frases: List<String>) {
        if (modoRobot != ModoRobot.ESTACION) return
        val escuchas = frases.map { ComandosVoz.escuchar(it) }
        val llamadas = escuchas.filter { it.llamado }
        val sinNombre = SystemClock.elapsedRealtime() < esperandoOrdenHastaMs
        // Con el nombre vale cualquier orden. Justo después de tocar el micrófono, también sin
        // el nombre; y durante una guía, "siguiente", "repite" o "para" sin decir el nombre.
        var comando = llamadas.firstNotNullOfOrNull { ComandosVoz.interpretar(it.resto) }
        if (comando == null && (sinNombre || guiaActual != null || metronomoActivo)) {
            comando = escuchas.firstNotNullOfOrNull { ComandosVoz.interpretar(it.resto) }
                ?.takeIf { sinNombre || it in CONTROLES_GUIA }
        }
        when {
            comando != null -> {
                esperandoOrdenHastaMs = 0L
                vecesNoEntendi = 0
                ejecutarComando(comando)
            }
            llamadas.any { it.resto.isBlank() } -> responder("¿Sí? Dime.", Expresion.ESCUCHANDO) { esperarOrden() }
            llamadas.isNotEmpty() || (sinNombre && vecesNoEntendi == 0) -> {
                esperandoOrdenHastaMs = 0L
                noEntendi()
            }
            caraRobot.expresion == Expresion.ESCUCHANDO -> caraRobot.expresion = expresionReposo()
        }
    }

    /** Botón del micrófono o tocar la cara: escucha una orden sin tener que decir "Ehria". */
    private fun escucharOrden() {
        if (modoRobot != ModoRobot.ESTACION) return
        if (!asistenteVozActivo) {
            Toast.makeText(this, "El asistente de voz está apagado (pestaña Estación)", Toast.LENGTH_SHORT).show()
            return
        }
        if (!permisoMicrofono()) {
            pedirMicrofono.launch(Manifest.permission.RECORD_AUDIO)
            return
        }
        despertar(saludar = false)
        // Si estaba respondiendo algo, se calla para escuchar (las guías siguen).
        if (vozActualId != null && !asistenteOcupado()) {
            try {
                tts?.stop()
            } catch (_: Exception) {
                // Ignorar
            }
        }
        sonidos.efecto(SintetizadorSonidos.escuchando(), forzar = true)
        esperarOrden()
    }

    private fun esperarOrden() {
        esperandoOrdenHastaMs = SystemClock.elapsedRealtime() + ESPERA_ORDEN_MS
        if (!asistenteOcupado()) {
            caraRobot.expresion = Expresion.ESCUCHANDO
            decirEnBurbuja("Te escucho…")
        }
        handler.removeCallbacks(finEsperaOrdenRunnable)
        handler.postDelayed(finEsperaOrdenRunnable, ESPERA_ORDEN_MS)
        programarSueno()
        actualizarEscucha()
    }

    private val finEsperaOrdenRunnable = Runnable {
        if (caraRobot.expresion == Expresion.ESCUCHANDO) caraRobot.expresion = expresionReposo()
        if (textoBurbuja == "Te escucho…") decirEnBurbuja("")
    }

    private fun ejecutarComando(comando: Comando) {
        handler.removeCallbacks(finEsperaOrdenRunnable)
        respuestaPendiente?.let { handler.removeCallbacks(it) }
        despertar(saludar = false)
        val control = comando in CONTROLES_GUIA
        // Un instante "pensando" antes de responder, para que se note que entendió.
        if (!control && !metronomoActivo) caraRobot.expresion = Expresion.PENSANDO
        val accion = Runnable {
            respuestaPendiente = null
            realizarComando(comando)
        }
        respuestaPendiente = accion
        handler.postDelayed(accion, if (control) 0L else PAUSA_PENSANDO_MS)
    }

    private fun realizarComando(comando: Comando) {
        when (comando) {
            Comando.ABRIR_BOTIQUIN -> when {
                !arduino.conectado ->
                    responder("No estoy conectada a la estación, así que no puedo abrir la puerta del botiquín.", Expresion.TRISTE)
                puertaAbierta -> responder("El botiquín ya está abierto. Toma lo que necesites.", Expresion.FELIZ)
                else -> {
                    abrirBotiquin(true)
                    responder("¡Listo! Abro el botiquín. Si quieres saber cómo usar algo, pregúntame qué tiene.", Expresion.FELIZ)
                }
            }
            Comando.CERRAR_BOTIQUIN -> when {
                !arduino.conectado ->
                    responder("No estoy conectada a la estación, así que no puedo mover la puerta del botiquín.", Expresion.TRISTE)
                !puertaAbierta -> responder("El botiquín ya está cerrado.", Expresion.GUINO)
                else -> {
                    abrirBotiquin(false)
                    responder("Cierro el botiquín. Cuidado con los dedos.", Expresion.GUINO)
                }
            }
            Comando.CONTENIDO_BOTIQUIN -> iniciarGuia(GuiasPrimerosAuxilios.BOTIQUIN)
            Comando.RCP -> iniciarGuia(GuiasPrimerosAuxilios.RCP)
            Comando.TORNIQUETE -> iniciarGuia(GuiasPrimerosAuxilios.TORNIQUETE)
            Comando.ATRAGANTAMIENTO -> iniciarGuia(GuiasPrimerosAuxilios.ATRAGANTAMIENTO)
            Comando.QUEMADURA -> iniciarGuia(GuiasPrimerosAuxilios.QUEMADURA)
            Comando.AMBULANCIA -> iniciarLlamadaSimulada()
            Comando.PUNTO_ENCUENTRO -> {
                val punto = puntoEncuentro
                if (punto == null) {
                    responder(
                        "Todavía no tengo un punto de encuentro. La brigada puede configurarlo en la pestaña Estación de la app.",
                        Expresion.TRISTE,
                    )
                } else {
                    cerrarExtrasRobot()
                    mostrarMapa(null)
                    responder(punto.descripcionVoz(), Expresion.CONCENTRADO) { programarOcultarMapa(MAPA_TRAS_RESPUESTA_MS) }
                }
            }
            Comando.CLIMA -> {
                val t = ultimaLectura?.temperaturaC
                val h = ultimaLectura?.humedad
                val calor = t != null && h != null && AsistenteEstacion.riesgoCalor(AsistenteEstacion.indiceCalor(t, h)) != null
                val expresion = when {
                    t == null && h == null -> Expresion.CONFUNDIDO
                    calor -> Expresion.CALOR
                    else -> Expresion.FELIZ
                }
                responder(AsistenteEstacion.respuestaClima(t, h), expresion)
            }
            Comando.SISMOS -> responder(
                AsistenteEstacion.sismos(datosAsistente()),
                if (sismosDeHoy().isEmpty()) Expresion.FELIZ else Expresion.PREOCUPADO,
            )
            Comando.HORA -> {
                val ahora = Calendar.getInstance()
                responder(AsistenteEstacion.hora(ahora.get(Calendar.HOUR_OF_DAY), ahora.get(Calendar.MINUTE)), Expresion.FELIZ)
            }
            Comando.PRESENTARSE -> responder(AsistenteEstacion.PRESENTACION, Expresion.FELIZ) {
                caraRobot.expresion = Expresion.GUINO
                handler.postDelayed(restaurarTrasRespuestaRunnable, 1_500L)
            }
            Comando.AYUDA -> mostrarAyuda()
            Comando.SALUDO -> responder("¡Hola! ¿En qué te ayudo?", Expresion.FELIZ) { esperarOrden() }
            Comando.GRACIAS -> {
                caraRobot.ritmoBpm = 70
                responder("¡Con gusto! Para eso estoy.", Expresion.CORAZON)
            }
            Comando.DETENER -> detenerAsistente(hablando = true)
            Comando.SIGUIENTE -> if (guiaActual != null) moverGuia(1) else noEntendi()
            Comando.ANTERIOR -> if (guiaActual != null) moverGuia(-1) else noEntendi()
            Comando.REPETIR -> when {
                guiaActual != null -> moverGuia(0)
                ultimaRespuesta.isNotEmpty() -> responder(ultimaRespuesta, Expresion.DESPIERTO)
                else -> noEntendi()
            }
        }
    }

    private fun datosAsistente() = AsistenteEstacion.Datos(
        temperaturaC = ultimaLectura?.temperaturaC,
        humedad = ultimaLectura?.humedad,
        horaDelDia = Calendar.getInstance().get(Calendar.HOUR_OF_DAY),
        sismosHoy = sismosDeHoy(),
        deteccionActiva = deteccionActiva,
        asistenteVoz = asistenteVozActivo && permisoMicrofono(),
    )

    /**
     * El robot contesta: subtítulo, expresión y voz. La voz suena aunque los efectos estén
     * silenciados, porque se la pidieron. [alTerminar] corre cuando termina de hablar.
     */
    private fun responder(texto: String, expresion: Expresion, alTerminar: (() -> Unit)? = null) {
        ultimaRespuesta = texto
        handler.removeCallbacks(restaurarTrasRespuestaRunnable)
        handler.removeCallbacks(limpiarSubtituloRunnable)
        handler.removeCallbacks(respuestaSinVozRunnable)
        caraRobot.expresion = expresion
        decirEnBurbuja(texto)
        accionTrasRespuesta = alTerminar
        programarSueno()
        if (!hablar(texto, ID_VOZ_ASISTENTE, forzar = true)) {
            // Sin voz: se deja tiempo para leer el subtítulo y se sigue como si hubiera hablado.
            handler.postDelayed(respuestaSinVozRunnable, 1_500L + texto.length * 60L)
        }
    }

    private val respuestaSinVozRunnable = Runnable { alTerminarRespuesta() }

    private fun alTerminarRespuesta() {
        val accion = accionTrasRespuesta
        accionTrasRespuesta = null
        if (accion != null) accion() else handler.postDelayed(restaurarTrasRespuestaRunnable, 1_200L)
        programarSueno()
    }

    private val restaurarTrasRespuestaRunnable = Runnable {
        if (modoRobot == ModoRobot.ESTACION) {
            caraRobot.expresion = expresionReposo()
            // El subtítulo se borra un rato después, para que alcance a leerse.
            handler.postDelayed(limpiarSubtituloRunnable, 4_000L)
        }
    }

    private val limpiarSubtituloRunnable = Runnable {
        if (vozActualId == null && textoBurbuja == ultimaRespuesta.trim()) decirEnBurbuja("")
    }

    private fun noEntendi() {
        val texto = AsistenteEstacion.NO_ENTENDI[vecesNoEntendi % AsistenteEstacion.NO_ENTENDI.size]
        vecesNoEntendi++
        // Solo la primera vez vuelve a escuchar sin el nombre (así una charla cercana no lo
        // deja respondiendo "no te entendí" una y otra vez).
        responder(texto, Expresion.CONFUNDIDO, if (vecesNoEntendi == 1) ({ esperarOrden() }) else null)
    }

    /** La cara a la que vuelve el robot cuando termina de responder. */
    private fun expresionReposo(): Expresion = when {
        ultimaFase == Fase.ALERTA -> Expresion.ALERTA
        metronomoActivo -> Expresion.CORAZON
        guiaActual != null || llamadaActiva -> Expresion.CONCENTRADO
        !robotDespierto -> Expresion.DORMIDO
        else -> Expresion.DESPIERTO
    }

    /** Hay una guía, el metrónomo, una llamada o el panel de ayuda en pantalla. */
    private fun asistenteOcupado() = guiaActual != null || metronomoActivo || llamadaActiva ||
        (::panelRobot.isInitialized && panelRobot.visibility == View.VISIBLE)

    // ---------- Guías paso a paso y RCP ----------

    private fun iniciarGuia(guia: Guia) {
        cerrarExtrasRobot()
        guiaActual = guia
        pasoGuia = 0
        decirEnBurbuja("")
        decirPasoGuia()
    }

    private fun decirPasoGuia() {
        val guia = guiaActual ?: return
        handler.removeCallbacks(avanzarGuiaRunnable)
        handler.removeCallbacks(cerrarPanelRunnable)
        val texto = guia.pasos[pasoGuia]
        val (color, icono) = estiloGuia(guia)
        ponerPanel(
            etiqueta = guia.titulo.uppercase(Locale.getDefault()),
            color = color,
            icono = icono,
            titulo = if (guia == GuiasPrimerosAuxilios.BOTIQUIN) "${pasoGuia + 1} de ${guia.pasos.size}"
            else "Paso ${pasoGuia + 1} de ${guia.pasos.size}",
            texto = texto,
            progreso = (pasoGuia + 1) * 100 / guia.pasos.size,
            pasos = true,
        )
        caraRobot.expresion = Expresion.CONCENTRADO
        programarSueno()
        if (!hablar(texto, ID_VOZ_GUIA, forzar = true)) handler.postDelayed(avanzarGuiaRunnable, 2_500L + texto.length * 65L)
    }

    private fun estiloGuia(guia: Guia): Pair<String, Int> = when (guia) {
        GuiasPrimerosAuxilios.RCP -> Pair("#F43F5E", R.drawable.ic_corazon)
        GuiasPrimerosAuxilios.TORNIQUETE -> Pair("#F87171", R.drawable.ic_botiquin)
        GuiasPrimerosAuxilios.ATRAGANTAMIENTO -> Pair("#38BDF8", R.drawable.ic_respiracion)
        GuiasPrimerosAuxilios.QUEMADURA -> Pair("#F97316", R.drawable.ic_fuego)
        else -> Pair("#22C55E", R.drawable.ic_botiquin)
    }

    private val avanzarGuiaRunnable = Runnable { moverGuia(1) }

    /** -1 = paso anterior, 0 = repetir el paso, 1 = siguiente. */
    private fun moverGuia(delta: Int) {
        val guia = guiaActual ?: return
        if (metronomoActivo) return
        handler.removeCallbacks(avanzarGuiaRunnable)
        val siguiente = pasoGuia + delta
        if (siguiente >= guia.pasos.size) {
            terminarPasosGuia()
            return
        }
        pasoGuia = siguiente.coerceAtLeast(0)
        decirPasoGuia()
    }

    private fun terminarPasosGuia() {
        val guia = guiaActual ?: return
        if (guia.metronomo) {
            iniciarMetronomoRcp()
        } else {
            // El último paso queda en pantalla un rato, para poder releerlo.
            handler.removeCallbacks(cerrarPanelRunnable)
            handler.postDelayed(cerrarPanelRunnable, PANEL_ABIERTO_AL_TERMINAR_MS)
        }
    }

    private val cerrarPanelRunnable = Runnable { detenerAsistente(hablando = false) }

    /** Suena el ritmo de las compresiones y los ojos laten con él, hasta "Ehria, para". */
    private fun iniciarMetronomoRcp() {
        handler.removeCallbacks(avanzarGuiaRunnable)
        metronomoActivo = true
        metronomoInicioMs = SystemClock.elapsedRealtime()
        proximoCambioRcpS = CAMBIO_REANIMADOR_S
        sonidos.iniciarMetronomo(GuiasPrimerosAuxilios.RITMO_RCP)
        caraRobot.ritmoBpm = GuiasPrimerosAuxilios.RITMO_RCP
        caraRobot.expresion = Expresion.CORAZON
        ponerPanel(
            etiqueta = "RCP · COMPRESIONES",
            color = "#F43F5E",
            icono = R.drawable.ic_corazon,
            titulo = "0:00",
            texto = "Empuja fuerte y rápido al ritmo del sonido: unos 5 cm, 100 a 120 veces por minuto. " +
                "Di «Ehria, para» cuando llegue ayuda o la persona respire.",
            progreso = null,
            pasos = false,
        )
        handler.removeCallbacks(relojMetronomoRunnable)
        handler.postDelayed(relojMetronomoRunnable, 1_000L)
    }

    private var proximoCambioRcpS = CAMBIO_REANIMADOR_S

    private val relojMetronomoRunnable = object : Runnable {
        override fun run() {
            if (!metronomoActivo) return
            val segundos = (SystemClock.elapsedRealtime() - metronomoInicioMs) / 1000L
            tvPanelTitulo.text = String.format(Locale.getDefault(), "%d:%02d", segundos / 60, segundos % 60)
            programarSueno()
            if (segundos >= METRONOMO_MAXIMO_S) {
                detenerAsistente(hablando = true)
                return
            }
            // Cada 2 minutos conviene turnarse: cansado, se empuja con menos fuerza.
            if (segundos >= proximoCambioRcpS) {
                proximoCambioRcpS += CAMBIO_REANIMADOR_S
                responder("Si hay alguien más, cámbiense ahora sin dejar de empujar.", Expresion.CORAZON)
            }
            handler.postDelayed(this, 1_000L)
        }
    }

    // ---------- Llamada simulada y ayuda ----------

    /** Simula la llamada a una ambulancia y deja claro que no se llamó de verdad. */
    private fun iniciarLlamadaSimulada() {
        cerrarExtrasRobot()
        llamadaActiva = true
        etapaLlamada = 0
        ponerPanel(
            etiqueta = "LLAMADA SIMULADA",
            color = "#F43F5E",
            icono = R.drawable.ic_telefono,
            titulo = AsistenteEstacion.NUMERO_EMERGENCIA,
            texto = "Llamando a una ambulancia de la ${AsistenteEstacion.NOMBRE_EMERGENCIA}…",
            progreso = -1,
            pasos = false,
        )
        responder(AsistenteEstacion.LLAMANDO, Expresion.CONCENTRADO) { handler.post(llamadaRunnable) }
    }

    private val llamadaRunnable = object : Runnable {
        override fun run() {
            if (!llamadaActiva) return
            when (etapaLlamada++) {
                0 -> {
                    val tono = SintetizadorSonidos.tonoLlamada()
                    sonidos.efecto(tono, forzar = true)
                    handler.postDelayed(this, SintetizadorSonidos.duracionMs(tono) + 300L)
                }
                1 -> {
                    pbPanel.visibility = View.GONE
                    tvPanelTexto.text = "Simulación: no se hizo una llamada real. Si es una emergencia, " +
                        "llama tú al ${AsistenteEstacion.NUMERO_EMERGENCIA} desde un teléfono."
                    responder(AsistenteEstacion.LLAMADA_SIMULADA, Expresion.CONCENTRADO) {
                        handler.postDelayed(cerrarPanelRunnable, PANEL_ABIERTO_AL_TERMINAR_MS)
                    }
                }
            }
        }
    }

    private fun mostrarAyuda() {
        cerrarExtrasRobot()
        ponerPanel(
            etiqueta = "PUEDES DECIRME",
            color = "#22D3EE",
            icono = R.drawable.ic_microfono,
            titulo = "¿En qué te ayudo?",
            texto = AsistenteEstacion.EJEMPLOS.take(6).joinToString("\n") { "«$it»" },
            progreso = null,
            pasos = false,
        )
        responder(AsistenteEstacion.AYUDA, Expresion.FELIZ) {
            caraRobot.expresion = expresionReposo()
            handler.postDelayed(cerrarPanelRunnable, PANEL_ABIERTO_AL_TERMINAR_MS)
        }
    }

    /** [progreso]: null = sin barra, -1 = barra en movimiento (sin final). */
    private fun ponerPanel(
        etiqueta: String, color: String, icono: Int, titulo: String, texto: String, progreso: Int?, pasos: Boolean,
    ) {
        val c = Color.parseColor(color)
        tvPanelEtiqueta.text = etiqueta
        tvPanelEtiqueta.setTextColor(c)
        tvPanelEtiqueta.setCompoundDrawablesRelativeWithIntrinsicBounds(icono, 0, 0, 0)
        tvPanelEtiqueta.compoundDrawableTintList = ColorStateList.valueOf(c)
        tvPanelTitulo.text = titulo
        tvPanelTexto.text = texto
        // La barra solo puede pasar a "en movimiento" mientras está oculta.
        pbPanel.visibility = View.GONE
        if (progreso != null) {
            pbPanel.isIndeterminate = progreso < 0
            if (progreso >= 0) pbPanel.progress = progreso
            pbPanel.setIndicatorColor(c)
            pbPanel.visibility = View.VISIBLE
        }
        val controles = if (pasos) View.VISIBLE else View.GONE
        btnPanelAnterior.visibility = controles
        btnPanelRepetir.visibility = controles
        btnPanelSiguiente.visibility = controles
        mostrarPanelRobot(true)
    }

    /** Con el panel abierto, la cara se corre a la izquierda y se achica un poco. */
    private fun mostrarPanelRobot(visible: Boolean) {
        if (visible == (panelRobot.visibility == View.VISIBLE)) return
        panelRobot.visibility = if (visible) View.VISIBLE else View.GONE
        contenedorSubtitulos.visibility = if (visible) View.GONE else View.VISIBLE
        if (visible) ocultarSismografoRobot()
        val escala = if (visible) 0.8f else 1f
        caraRobot.animate()
            .translationX(if (visible) -layoutRobot.width * 0.24f else 0f)
            .scaleX(escala)
            .scaleY(escala)
            .setDuration(350L)
            .start()
    }

    /** Cierra la guía, el metrónomo, la llamada, el panel y el mapa, sin decir nada. */
    private fun cerrarExtrasRobot() {
        if (!::panelRobot.isInitialized) return
        handler.removeCallbacks(avanzarGuiaRunnable)
        handler.removeCallbacks(cerrarPanelRunnable)
        handler.removeCallbacks(llamadaRunnable)
        handler.removeCallbacks(relojMetronomoRunnable)
        respuestaPendiente?.let { handler.removeCallbacks(it) }
        respuestaPendiente = null
        accionTrasRespuesta = null
        guiaActual = null
        llamadaActiva = false
        if (metronomoActivo) {
            metronomoActivo = false
            sonidos.detenerMetronomo()
        }
        mostrarPanelRobot(false)
        ocultarMapa()
    }

    /** "Ehria, para" o el botón Terminar: corta la voz, la guía, el metrónomo y la llamada. */
    private fun detenerAsistente(hablando: Boolean) {
        val eraRcp = metronomoActivo
        val habiaAlgo = asistenteOcupado() || layoutMapa.visibility == View.VISIBLE || vozActualId != null
        cerrarExtrasRobot()
        try {
            tts?.stop()
        } catch (_: Exception) {
            // Ignorar
        }
        when {
            hablando && eraRcp -> responder(
                "Muy bien. Si la persona empieza a respirar, ponla de lado y vigílala hasta que llegue ayuda.",
                Expresion.FELIZ,
            )
            hablando && habiaAlgo -> responder("De acuerdo.", Expresion.DESPIERTO)
            else -> {
                caraRobot.expresion = expresionReposo()
                decirEnBurbuja("")
                programarSueno()
            }
        }
    }

    // ---------- Punto de encuentro ----------

    /** Muestra el mapa a pantalla completa durante [ms] (null = hasta que se oculte). */
    private fun mostrarMapa(ms: Long?) {
        val punto = puntoEncuentro ?: return
        if (modoRobot == ModoRobot.NINGUNO) return
        val croquis = cargarCroquis()
        ivCroquis.setImageBitmap(croquis)
        ivCroquis.visibility = if (croquis != null) View.VISIBLE else View.GONE
        mapaEncuentro.visibility = if (croquis != null) View.GONE else View.VISIBLE
        mapaEncuentro.punto = punto
        tvMapaNombre.text = punto.nombre.replaceFirstChar { it.uppercase() }
        tvMapaDireccion.text = "${punto.direccion.flecha}  ${punto.ubicacion()}"
        tvMapaIndicaciones.text = punto.indicaciones
        tvMapaIndicaciones.visibility = if (punto.indicaciones.isBlank()) View.GONE else View.VISIBLE
        if (layoutMapa.visibility != View.VISIBLE) {
            layoutMapa.alpha = 0f
            layoutMapa.visibility = View.VISIBLE
        }
        layoutMapa.animate().alpha(1f).setDuration(300L).start()
        brilloPantalla(1f)
        handler.removeCallbacks(ocultarMapaRunnable)
        if (ms != null) handler.postDelayed(ocultarMapaRunnable, ms)
    }

    private fun programarOcultarMapa(ms: Long) {
        handler.removeCallbacks(ocultarMapaRunnable)
        handler.postDelayed(ocultarMapaRunnable, ms)
    }

    private val ocultarMapaRunnable = Runnable { ocultarMapa() }

    private fun ocultarMapa() {
        if (!::layoutMapa.isInitialized) return
        handler.removeCallbacks(ocultarMapaRunnable)
        if (layoutMapa.visibility != View.VISIBLE) return
        layoutMapa.animate().alpha(0f).setDuration(300L).withEndAction { layoutMapa.visibility = View.GONE }.start()
        programarSueno()
    }

    private fun actualizarTarjetaEncuentro() {
        val punto = puntoEncuentro
        val color = Color.parseColor(if (punto != null) "#22C55E" else "#94A3B8")
        tvEncuentroNombre.text = punto?.nombre?.replaceFirstChar { it.uppercase() } ?: "Sin configurar"
        tvEncuentroDetalle.text = punto?.let {
            it.ubicacion() + if (archivoCroquis().exists()) " · con croquis" else ""
        } ?: "Durante la alarma y la evacuación, la pantalla del robot muestra cómo llegar."
        ivEncuentro.imageTintList = ColorStateList.valueOf(color)
        circuloEncuentro.backgroundTintList = ColorStateList.valueOf(conAlfa(color, 0x26))
        findViewById<Button>(R.id.btnVerMapa).isEnabled = punto != null
    }

    private fun configurarPuntoEncuentro() {
        val vista = layoutInflater.inflate(R.layout.dialogo_punto_encuentro, null)
        val etNombre = vista.findViewById<EditText>(R.id.etEncuentroNombre)
        val etDistancia = vista.findViewById<EditText>(R.id.etEncuentroDistancia)
        val etIndicaciones = vista.findViewById<EditText>(R.id.etEncuentroIndicaciones)
        val grid = vista.findViewById<GridLayout>(R.id.gridDireccion)
        val ivVista = vista.findViewById<ImageView>(R.id.ivCroquisVista)
        val btnQuitar = vista.findViewById<Button>(R.id.btnQuitarCroquis)
        val actual = puntoEncuentro
        var direccion = actual?.direccion ?: PuntoEncuentro.Direccion.FRENTE
        etNombre.setText(actual?.nombre.orEmpty())
        etDistancia.setText(actual?.distanciaM?.toString().orEmpty())
        etIndicaciones.setText(actual?.indicaciones.orEmpty())

        val botones = (0 until grid.childCount).map { grid.getChildAt(it) }.filterIsInstance<MaterialButton>()
        fun marcarDireccion() {
            for (b in botones) b.isChecked = b.tag == direccion.name
        }
        for (b in botones) {
            b.setOnClickListener {
                direccion = PuntoEncuentro.Direccion.valueOf(b.tag as String)
                marcarDireccion()
            }
        }
        marcarDireccion()

        // La foto elegida va a un archivo temporal y solo reemplaza al croquis al guardar.
        val temporal = File(filesDir, "croquis_nuevo.jpg").also { it.delete() }
        var quitarCroquis = false
        fun mostrarVistaCroquis() {
            val archivo = when {
                temporal.exists() -> temporal
                !quitarCroquis && archivoCroquis().exists() -> archivoCroquis()
                else -> null
            }
            val imagen = archivo?.let { BitmapFactory.decodeFile(it.path) }
            ivVista.setImageBitmap(imagen)
            ivVista.visibility = if (imagen != null) View.VISIBLE else View.GONE
            btnQuitar.visibility = ivVista.visibility
        }
        mostrarVistaCroquis()
        alElegirCroquis = { uri ->
            if (uri != null) {
                if (guardarImagenReducida(uri, temporal)) {
                    quitarCroquis = false
                    mostrarVistaCroquis()
                } else {
                    Toast.makeText(this, "No se pudo abrir la foto", Toast.LENGTH_SHORT).show()
                }
            }
        }
        vista.findViewById<Button>(R.id.btnElegirCroquis).setOnClickListener {
            elegirFoto.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
        }
        btnQuitar.setOnClickListener {
            temporal.delete()
            quitarCroquis = true
            mostrarVistaCroquis()
        }

        val dialogo = AlertDialog.Builder(this)
            .setTitle("Punto de encuentro")
            .setView(vista)
            .setPositiveButton("Guardar", null)
            .setNegativeButton("Cancelar", null)
            .apply { if (actual != null) setNeutralButton("Borrar", null) }
            .create()
        dialogo.setOnShowListener {
            // Se valida antes de cerrar: sin nombre no se guarda.
            dialogo.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
                val nombre = etNombre.text.toString().trim()
                if (nombre.isEmpty()) {
                    etNombre.error = "Escribe el nombre del lugar"
                    return@setOnClickListener
                }
                val punto = PuntoEncuentro(
                    nombre = nombre,
                    direccion = direccion,
                    distanciaM = etDistancia.text.toString().toIntOrNull()?.takeIf { it > 0 },
                    indicaciones = etIndicaciones.text.toString().trim(),
                )
                punto.guardar(getPreferences(MODE_PRIVATE))
                if (temporal.exists()) {
                    archivoCroquis().delete()
                    temporal.renameTo(archivoCroquis())
                } else if (quitarCroquis) {
                    archivoCroquis().delete()
                }
                croquisCargado = false
                puntoEncuentro = punto
                actualizarTarjetaEncuentro()
                dialogo.dismiss()
                Toast.makeText(this, "Punto de encuentro guardado", Toast.LENGTH_SHORT).show()
            }
            dialogo.getButton(AlertDialog.BUTTON_NEUTRAL)?.setOnClickListener {
                PuntoEncuentro.borrar(getPreferences(MODE_PRIVATE))
                archivoCroquis().delete()
                croquisCargado = false
                puntoEncuentro = null
                actualizarTarjetaEncuentro()
                dialogo.dismiss()
            }
        }
        dialogo.setOnDismissListener {
            temporal.delete()
            alElegirCroquis = null
        }
        dialogo.show()
    }

    /** Vista previa desde la pestaña Estación (sin entrar al modo robot). */
    private fun verMapaPrevia() {
        val punto = puntoEncuentro ?: return
        val alto = (240 * resources.displayMetrics.density).toInt()
        val croquis = cargarCroquis()
        val dibujo: View = if (croquis != null) {
            ImageView(this).apply {
                setImageBitmap(croquis)
                scaleType = ImageView.ScaleType.FIT_CENTER
            }
        } else {
            MapaEncuentroView(this).apply {
                this.punto = punto
                setBackgroundColor(Color.parseColor("#04070D"))
            }
        }
        val contenedor = android.widget.FrameLayout(this).apply {
            addView(dibujo, android.widget.FrameLayout.LayoutParams(android.widget.FrameLayout.LayoutParams.MATCH_PARENT, alto))
        }
        AlertDialog.Builder(this)
            .setTitle(punto.nombre.replaceFirstChar { it.uppercase() })
            .setMessage(punto.descripcionVoz())
            .setView(contenedor)
            .setPositiveButton("Cerrar", null)
            .show()
    }

    private fun archivoCroquis() = File(filesDir, "croquis_encuentro.jpg")

    private var croquis: Bitmap? = null
    private var croquisCargado = false

    private fun cargarCroquis(): Bitmap? {
        if (!croquisCargado) {
            croquis = archivoCroquis().takeIf { it.exists() }?.let { BitmapFactory.decodeFile(it.path) }
            croquisCargado = true
        }
        return croquis
    }

    /** Copia la foto reducida (máximo ~1600 px) y derecha según su orientación EXIF. */
    private fun guardarImagenReducida(uri: Uri, destino: File): Boolean = try {
        val limites = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, limites) }
        var muestra = 1
        while (limites.outWidth / (muestra * 2) >= LADO_MAXIMO_CROQUIS || limites.outHeight / (muestra * 2) >= LADO_MAXIMO_CROQUIS) {
            muestra *= 2
        }
        val giro = contentResolver.openInputStream(uri)?.use {
            when (ExifInterface(it).getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL)) {
                ExifInterface.ORIENTATION_ROTATE_90 -> 90f
                ExifInterface.ORIENTATION_ROTATE_180 -> 180f
                ExifInterface.ORIENTATION_ROTATE_270 -> 270f
                else -> 0f
            }
        } ?: 0f
        val original = contentResolver.openInputStream(uri)?.use {
            BitmapFactory.decodeStream(it, null, BitmapFactory.Options().apply { inSampleSize = muestra })
        }
        if (original == null) {
            false
        } else {
            val derecha = if (giro == 0f) original else {
                Bitmap.createBitmap(original, 0, 0, original.width, original.height, Matrix().apply { postRotate(giro) }, true)
            }
            destino.outputStream().use { derecha.compress(Bitmap.CompressFormat.JPEG, 85, it) }
            true
        }
    } catch (_: Exception) {
        false
    }

    // ---------- Voz: quién está hablando ----------

    private fun alEmpezarVoz(id: String) {
        vozActualId = id
        handler.removeCallbacks(reanudarEscuchaRunnable)
        handler.removeCallbacks(vozSinAvisoRunnable)
        handler.postDelayed(vozSinAvisoRunnable, VOZ_MAXIMA_MS)
        actualizarEscucha()
    }

    /** Llamado por la voz al terminar ([completa]) o al cortarse una frase. */
    private fun alTerminarVoz(id: String?, completa: Boolean) {
        // Una frase vieja que se cortó porque empezó otra: no cambia nada.
        if (id == null || id != vozActualId) return
        vozActualId = null
        finVozMs = SystemClock.elapsedRealtime() + MARGEN_VIBRACION_VOZ_MS
        handler.removeCallbacks(vozSinAvisoRunnable)
        caraRobot.hablando = false
        sonidos.volumenSirena(1f)
        // Un momento de silencio antes de volver a escuchar, para no oír el final de la voz.
        handler.removeCallbacks(reanudarEscuchaRunnable)
        handler.postDelayed(reanudarEscuchaRunnable, 400L)
        when (id.substringBefore('#')) {
            ID_VOZ_RESULTADO -> if (completa) {
                accionTrasVoz?.invoke()
                accionTrasVoz = null
            }
            ID_VOZ_GUIA -> if (completa) handler.postDelayed(avanzarGuiaRunnable, PAUSA_ENTRE_PASOS_MS)
            ID_VOZ_ASISTENTE -> alTerminarRespuesta()
        }
    }

    /** Por si la voz nunca avisa que terminó. */
    private val vozSinAvisoRunnable = Runnable { alTerminarVoz(vozActualId, completa = false) }

    /** true mientras sale sonido del parlante (o acaba de salir: la vibración tarda en apagarse). */
    private fun parlanteSonando() =
        vozActualId != null || SystemClock.elapsedRealtime() < finVozMs || sonidos.sonando()

    /** Si la alerta se cortó con "restablecer" o al pausar, la cara no se queda en alerta. */
    private fun restablecerCaraTrasSismo() {
        handler.removeCallbacks(mapaDuranteSismoRunnable)
        if (!::layoutMapa.isInitialized) return
        ocultarMapa()
        if (caraRobot.expresion == Expresion.ALERTA) caraRobot.expresion = expresionReposo()
    }

    /** Solo en compilaciones de depuración: permite simular el Arduino desde la computadora. */
    private fun registrarSimuladorSiDepuracion() {
        if (applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE == 0) return
        val receptor = object : BroadcastReceiver() {
            override fun onReceive(context: Context, intent: Intent) {
                intent.getStringExtra("linea")?.let { arduino.simularLinea(it) }
                if (intent.hasExtra("conectado")) arduino.simularConexion(intent.getBooleanExtra("conectado", false))
                // "27,65,80": temperatura, humedad y distancia (-1 = nadie) 4 veces por segundo,
                // como el Arduino. "no" deja de enviar.
                intent.getStringExtra("ambiente")?.let { simularAmbiente(it) }
                // Una frase como si la hubieran dicho al micrófono: "ehria abre el botiquin".
                intent.getStringExtra("decir")?.let { frase ->
                    if (modoRobot == ModoRobot.ESTACION) {
                        despertar(saludar = false)
                        alOirParcial(frase)
                        handler.postDelayed({ alOirFrase(listOf(frase)) }, 600L)
                    }
                }
                // Un nombre de CaraRobotView.Expresion, o "todas" para recorrerlas.
                intent.getStringExtra("expresion")?.let { simularExpresion(it) }
                // "leve", "moderado" o "fuerte": sacudida sintética de [segundos] (por defecto 14).
                intent.getStringExtra("sismo")?.let { simularSismo(it, intent.getIntExtra("segundos", 14)) }
            }
        }
        ContextCompat.registerReceiver(
            this, receptor, IntentFilter(ACCION_SIMULAR_ARDUINO), ContextCompat.RECEIVER_EXPORTED
        )
        receptorSimulador = receptor
    }

    // ---------- Simulador (solo depuración) ----------

    private var sismoSimuladoHastaMs = 0L
    private var lineaAmbienteSimulada: String? = null

    private val ambienteSimuladoRunnable = object : Runnable {
        override fun run() {
            val linea = lineaAmbienteSimulada ?: return
            arduino.simularLinea(linea)
            handler.postDelayed(this, 250L)
        }
    }

    private fun simularAmbiente(valores: String) {
        handler.removeCallbacks(ambienteSimuladoRunnable)
        lineaAmbienteSimulada = if (valores.equals("no", ignoreCase = true)) null else "S,$valores"
        if (lineaAmbienteSimulada != null) handler.post(ambienteSimuladoRunnable)
    }

    private fun simularExpresion(nombre: String) {
        if (modoRobot == ModoRobot.NINGUNO) entrarModoEstacion()
        despertar(saludar = false)
        handler.removeCallbacks(recorrerExpresionesRunnable)
        if (nombre.equals("todas", ignoreCase = true)) {
            indiceExpresionSimulada = 0
            handler.post(recorrerExpresionesRunnable)
            return
        }
        val expresion = Expresion.entries.firstOrNull { it.name.equals(nombre, ignoreCase = true) } ?: return
        caraRobot.ritmoBpm = GuiasPrimerosAuxilios.RITMO_RCP
        caraRobot.expresion = expresion
        decirEnBurbuja(expresion.name)
    }

    private var indiceExpresionSimulada = 0

    private val recorrerExpresionesRunnable = object : Runnable {
        override fun run() {
            val todas = Expresion.entries
            if (indiceExpresionSimulada >= todas.size) {
                caraRobot.expresion = expresionReposo()
                decirEnBurbuja("")
                return
            }
            val expresion = todas[indiceExpresionSimulada++]
            caraRobot.ritmoBpm = GuiasPrimerosAuxilios.RITMO_RCP
            caraRobot.expresion = expresion
            // Para ver la boca de "escuchando" moverse.
            if (expresion == Expresion.ESCUCHANDO) {
                for (i in 0 until 12) handler.postDelayed({ caraRobot.nivelVoz = (0.2f + Math.random().toFloat() * 0.8f) }, i * 200L)
            }
            decirEnBurbuja(expresion.name)
            programarSueno()
            handler.postDelayed(this, 2_500L)
        }
    }

    /** Sacudida sintética a 50 muestras por segundo, sin mover el teléfono. */
    private fun simularSismo(nivel: String, segundos: Int) {
        val amplitud = when (nivel.lowercase(Locale.ROOT)) {
            "fuerte" -> 2.2f
            "moderado" -> 0.7f
            else -> 0.2f
        }
        val inicio = SystemClock.elapsedRealtime()
        sismoSimuladoHastaMs = inicio + segundos * 1000L
        val gravedad = if (isLinearAvailable) 0f else SensorManager.GRAVITY_EARTH
        val paso = object : Runnable {
            override fun run() {
                val ahora = SystemClock.elapsedRealtime()
                if (ahora >= sismoSimuladoHastaMs) {
                    sismoSimuladoHastaMs = 0L
                    return
                }
                val t = (ahora - inicio) / 1000.0
                val a = amplitud * (0.7f + 0.3f * Math.random().toFloat())
                // Con la detección apagada o en pausa, tampoco se "siente" el sismo simulado.
                if (!sismosEnPausa) procesarMuestraSismica(
                    (a * kotlin.math.sin(2 * Math.PI * 2.1 * t)).toFloat(),
                    (a * 0.6 * kotlin.math.sin(2 * Math.PI * 3.3 * t + 1)).toFloat(),
                    gravedad + (a * 0.4 * kotlin.math.sin(2 * Math.PI * 1.7 * t + 2)).toFloat(),
                )
                handler.postDelayed(this, 20L)
            }
        }
        handler.post(paso)
    }

    // ===================== SIGNOS VITALES =====================

    private fun mostrarPanel(tipo: TipoMedicion) {
        panelSignos = tipo
        val respiracion = tipo == TipoMedicion.RESPIRACION
        layoutPanelRespiracion.visibility = if (respiracion) View.VISIBLE else View.GONE
        layoutPanelPulso.visibility = if (respiracion) View.GONE else View.VISIBLE
        pbBiometricProgress.setIndicatorColor(Color.parseColor(if (respiracion) "#38BDF8" else "#F43F5E"))
        if (::grupoModoRobot.isInitialized) {
            grupoModoRobot.check(if (respiracion) R.id.btnRobotRespiracion else R.id.btnRobotPulso)
            pbRobot.setIndicatorColor(Color.parseColor(if (respiracion) "#38BDF8" else "#F43F5E"))
            actualizarGuiaRobot()
            mostrarResultadoRobot()
            actualizarBotonesMedicion()
        }
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
        if (::grupoSexoRobot.isInitialized) {
            grupoSexoRobot.check(if (mujer) R.id.btnRobotMujer else R.id.btnRobotHombre)
            actualizarGuiaRobot()
        }
    }

    /** El botón de medir se convierte en "Detener" mientras se mide. */
    private fun alTocarMedir(tipo: TipoMedicion) {
        when {
            medicionActiva -> cancelarMedicion("Medición detenida.")
            ::arduino.isInitialized && arduino.conectado ->
                Toast.makeText(this, "Saca el teléfono de la estación para medir", Toast.LENGTH_SHORT).show()
            else -> iniciarMedicion(tipo)
        }
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
        if (!visibles) scrollSignos.let { s -> s.post { s.smoothScrollTo(0, 0) } }
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
        if (::btnMedirRobot.isInitialized) {
            val respiracion = panelSignos == TipoMedicion.RESPIRACION
            configurarBotonMedir(
                btnMedirRobot, medicionActiva,
                if (respiracion) "Medir respiración" else "Medir pulso",
                if (respiracion) "#0284C7" else "#BE123C",
            )
            if (medicionActiva) btnMedirRobot.text = "Detener"
            btnMedirRobot.isEnabled = hayAcelerometro
            for (i in 0 until grupoModoRobot.childCount) grupoModoRobot.getChildAt(i).isEnabled = !medicionActiva
            for (i in 0 until grupoSexoRobot.childCount) grupoSexoRobot.getChildAt(i).isEnabled = !medicionActiva
        }
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
        restablecerCaraTrasSismo()
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
        pbRobot.progress = 0
        pbRobot.setIndicatorColor(Color.parseColor(if (respiracion) "#38BDF8" else "#F43F5E"))
        pbRobot.visibility = View.VISIBLE
        mostrarOndaVital(true)
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
                    pbRobot.progress = pbBiometricProgress.progress
                    tvCuenta.text = "${((DURACION_RESPIRACION_MS - transcurrido + 999) / 1000).coerceAtLeast(0)} s"
                } else {
                    // El pulso se mide sin límite: la barra muestra cuánto falta para el primer análisis.
                    pbBiometricProgress.progress = ((transcurrido * 100) / PRIMER_ANALISIS_PULSO_MS).toInt().coerceIn(0, 100)
                    pbRobot.progress = pbBiometricProgress.progress
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
        pbRobot.visibility = View.INVISIBLE
        mostrarOndaVital(false)
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
        actualizarGuiaRobot()
        actualizarBotonesMedicion()
        reanudarSismos()
        // Al reaparecer las instrucciones, llevar la vista al resultado.
        val caja = if (tipoActual == TipoMedicion.RESPIRACION) cajaResultadoRespiracion else cajaResultadoPulso
        val scroll = scrollSignos
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
            decirEnBurbuja(mensaje)
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
        if (modoRobot == ModoRobot.HERRAMIENTA) {
            picoOndaVital = maxOf(picoOndaVital * 0.997f, abs(valor))
            sismografoRobot.rango = maxOf(0.01f, picoOndaVital * 1.25f)
            sismografoRobot.agregarPunto(valor)
        }
    }

    /** En primeros auxilios, el movimiento del pecho pasa por detrás de la cara mientras se mide. */
    private fun mostrarOndaVital(visible: Boolean) {
        if (!::sismografoRobot.isInitialized) return
        if (visible && modoRobot == ModoRobot.HERRAMIENTA) {
            sismografoRobot.limpiar()
            picoOndaVital = 0f
            sismografoRobot.setStrokeColor(if (tipoActual == TipoMedicion.RESPIRACION) "#38BDF8" else "#F43F5E")
            // Se desvanece antes de llegar a la guía y al resultado de los costados.
            sismografoRobot.bordeDesvanecido = 0.27f
            sismografoRobot.visibility = View.VISIBLE
        } else if (modoRobot != ModoRobot.ESTACION) {
            sismografoRobot.visibility = View.GONE
        }
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
        if (modoRobot == ModoRobot.HERRAMIENTA && ::tvGuiaRobot.isInitialized) tvGuiaRobot.text = texto
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
    /** [forzar]: suena aunque los efectos estén silenciados (respuestas a una orden de voz). */
    private fun hablar(texto: String, id: String = "ehria", forzar: Boolean = false): Boolean {
        if (!ttsListo || isBiometricMeasuring || (!sonidos.efectosActivos && !forzar)) return false
        return try {
            val motor = tts ?: return false
            // Durante una alarma, también por el canal de alarma para que se oiga igual de fuerte.
            motor.setAudioAttributes(atributosVoz(alarma = alarmaSonando))
            if (alarmaSonando) sonidos.volumenSirena(VOLUMEN_SIRENA_BAJO_VOZ)
            textoEnVoz = texto
            val idUnico = "$id#${++contadorVoz}"
            val ok = motor.speak(texto, TextToSpeech.QUEUE_FLUSH, null, idUnico) == TextToSpeech.SUCCESS
            if (ok) alEmpezarVoz(idUnico)
            ok
        } catch (_: Exception) {
            false
        }
    }

    /**
     * Voz de emergencia: suena aunque los sonidos estén silenciados. Va por el canal de alarma
     * (el mismo de la sirena, que suele estar al máximo) y la sirena baja mientras habla.
     */
    private fun hablarAlerta(texto: String) {
        if (!ttsListo) return
        try {
            val motor = tts ?: return
            motor.setAudioAttributes(atributosVoz(alarma = true))
            sonidos.volumenSirena(VOLUMEN_SIRENA_BAJO_VOZ)
            textoEnVoz = texto
            val parametros = Bundle().apply { putFloat(TextToSpeech.Engine.KEY_PARAM_VOLUME, 1f) }
            val idUnico = "$ID_VOZ_ALERTA#${++contadorVoz}"
            if (motor.speak(texto, TextToSpeech.QUEUE_FLUSH, parametros, idUnico) == TextToSpeech.SUCCESS) {
                alEmpezarVoz(idUnico)
            } else {
                sonidos.volumenSirena(1f)
            }
        } catch (_: Exception) {
            sonidos.volumenSirena(1f)
        }
    }

    private fun atributosVoz(alarma: Boolean): AudioAttributes = AudioAttributes.Builder()
        .setUsage(if (alarma) AudioAttributes.USAGE_ALARM else AudioAttributes.USAGE_MEDIA)
        .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
        .build()

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
                        if (alarmaSonando) sonidos.volumenSirena(VOLUMEN_SIRENA_BAJO_VOZ)
                    }
                }

                override fun onRangeStart(id: String?, inicio: Int, fin: Int, cuadro: Int) {
                    handler.post { alAvanzarVoz(inicio) }
                }

                override fun onDone(id: String?) {
                    handler.post { alTerminarVoz(id, completa = true) }
                }

                // Se cortó: empezó otra frase (QUEUE_FLUSH) o se pidió silencio.
                override fun onStop(id: String?, interrupted: Boolean) {
                    handler.post { alTerminarVoz(id, completa = false) }
                }

                @Deprecated("Deprecated in Java")
                override fun onError(id: String?) {
                    handler.post { alTerminarVoz(id, completa = false) }
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
        val tipo = if (valor == tvRpmValues) TipoMedicion.RESPIRACION else TipoMedicion.PULSO
        valoresRobot[tipo] = Triple(texto, textoEstado, color)
        if (tipo == panelSignos) mostrarResultadoRobot()
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
        enPrimerPlano = true
        actualizarEscucha()
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
        enPrimerPlano = false
        cerrarExtrasRobot()
        actualizarEscucha()
        // Guardar lo que lleva el sismo en curso, sin cerrarlo: si la alerta sigue al volver,
        // se actualiza el mismo registro en vez de crear uno duplicado.
        sismoActual?.let { historial.guardarSismo(it) }
    }

    override fun onDestroy() {
        super.onDestroy()
        handler.removeCallbacksAndMessages(null)
        sonidos.detenerTodo()
        if (::escucha.isInitialized) escucha.destruir()
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

        // 2. CAPTURA DE MUESTRAS PARA BIOMETRÍA (los tres ejes, con su hora exacta)
        if (sensorType == Sensor.TYPE_ACCELEROMETER && isBiometricMeasuring) {
            tiemposNs.add(event.timestamp)
            muestrasX.add(event.values[0])
            muestrasY.add(event.values[1])
            muestrasZ.add(event.values[2])
            alimentarMonitor(event.timestamp, event.values[0], event.values[1], event.values[2])
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
        const val ID_VOZ_ASISTENTE = "asistente"
        const val ID_VOZ_GUIA = "guia"
        val CONTROLES_GUIA = setOf(Comando.DETENER, Comando.SIGUIENTE, Comando.ANTERIOR, Comando.REPETIR)
        const val VOZ_MAXIMA_MS = 60_000L
        const val ESPERA_ORDEN_MS = 10_000L
        const val PAUSA_PENSANDO_MS = 450L
        const val PAUSA_ENTRE_PASOS_MS = 1_200L
        const val PANEL_ABIERTO_AL_TERMINAR_MS = 20_000L
        const val CAMBIO_REANIMADOR_S = 120L
        const val METRONOMO_MAXIMO_S = 1_200L
        const val SIRENA_MAXIMA_MS = 60_000L
        // Mapa del punto de encuentro durante la alarma y la evacuación
        const val PRIMER_MAPA_EN_ALARMA_MS = 12_000L
        const val MAPA_EN_ALARMA_MS = 8_000L
        const val MAPA_EN_EVACUACION_MS = 12_000L
        const val MAPA_AL_TERMINAR_MS = 20_000L
        const val CARA_ENTRE_MAPAS_MS = 15_000L
        const val MAPA_TRAS_RESPUESTA_MS = 8_000L
        const val EVACUACION_MS = 180_000L
        const val ESPERA_TRAS_SISMO_MS = 20_000L
        /** Cuánto dura la cara preocupada después de un sismo. */
        const val CARA_PREOCUPADA_MS = 20_000L
        const val MARGEN_VIBRACION_VOZ_MS = 400L
        const val LADO_MAXIMO_CROQUIS = 1_600
        const val PREF_ASISTENTE_VOZ = "asistente_voz"
        // La sirena casi se apaga mientras habla la voz, para que las indicaciones se entiendan.
        private const val VOLUMEN_SIRENA_BAJO_VOZ = 0.12f
        const val ESPERA_PARA_DORMIR_MS = 45_000L
        // Una desconexión más corta se toma como falso contacto del cable.
        const val ESPERA_DESCONEXION_MS = 3_000L
        const val ESPERA_ENTRE_SALUDOS_MS = 60_000L
        const val ACCION_SIMULAR_ARDUINO = "com.example.detectormovimiento.SIMULAR_ARDUINO"
        /** Debe coincidir con herramientas/puente.ps1 y el "adb reverse" de ehria.bat. */
        const val PUERTO_PUENTE = 8777
        const val PREF_MUJER = "respiracion_mujer"
        const val FRECUENCIA_MIN_PULSO_HZ = 40.0
        const val PERIODO_BIOMETRIA_US = 5_000 // 200 Hz
    }
}
