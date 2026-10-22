package com.example.detectormovimiento

import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.hardware.usb.UsbDevice
import android.hardware.usb.UsbManager
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.util.Log
import androidx.core.content.ContextCompat
import com.hoho.android.usbserial.driver.UsbSerialPort
import com.hoho.android.usbserial.driver.UsbSerialProber
import com.hoho.android.usbserial.util.SerialInputOutputManager
import java.net.InetSocketAddress
import java.net.Socket
import java.util.concurrent.Executors
import kotlin.concurrent.thread

/**
 * Conexión por cable USB con el Arduino Uno de la estación (original o clon con CH340).
 *
 * - Pide permiso al usuario la primera vez que se conecta.
 * - Lee línea por línea en un hilo aparte y entrega los mensajes en el hilo principal.
 * - Avisa cuando el cable se conecta o se desconecta (por ejemplo, al sacar la "cabeza").
 * - Si la comunicación falla pero el cable sigue puesto (un bajón de voltaje al mover el
 *   servo, por ejemplo), se reconecta sola cada pocos segundos.
 * - Solo para pruebas: con [puertoPuente], también acepta el Arduino conectado a una
 *   computadora (herramientas/puente.ps1 y "adb reverse"), sin cable OTG.
 *
 * Nota: el teléfono alimenta al Arduino por el cable. El servo debe tener su propia fuente
 * de 5 V (con la tierra unida al Arduino), o se reinicia todo al moverse.
 */
class ConexionArduino(
    private val contexto: Context,
    private val alRecibir: (ProtocoloArduino.Mensaje) -> Unit,
    private val alCambiarEstado: (conectado: Boolean) -> Unit,
) {
    var conectado = false
        private set

    /** Puerto TCP del puente con la computadora (solo depuración); null = sin puente. */
    var puertoPuente: Int? = null

    // Puente: conexión abierta con la computadora (cuenta como conectada cuando llegan datos).
    private var puente: Socket? = null
    private val escritorPuente by lazy { Executors.newSingleThreadExecutor() }

    private val handler = Handler(Looper.getMainLooper())
    private val usb = contexto.getSystemService(Context.USB_SERVICE) as UsbManager
    private var puerto: UsbSerialPort? = null
    private var lector: SerialInputOutputManager? = null
    private val pendiente = StringBuilder()
    private var receptorRegistrado = false
    private var iniciada = false

    private val receptor = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            when (intent.action) {
                ACCION_PERMISO -> if (intent.getBooleanExtra(UsbManager.EXTRA_PERMISSION_GRANTED, false)) conectar()
                UsbManager.ACTION_USB_DEVICE_ATTACHED -> conectar()
                UsbManager.ACTION_USB_DEVICE_DETACHED -> {
                    // Solo si se desconectó el Arduino (no otro aparato USB).
                    val quitado = dispositivoDe(intent)
                    val propio = puerto?.driver?.device
                    if (quitado == null || propio == null || quitado.deviceId == propio.deviceId) desconectar()
                }
            }
        }
    }

    fun iniciar() {
        iniciada = true
        if (!receptorRegistrado) {
            val filtro = IntentFilter().apply {
                addAction(ACCION_PERMISO)
                addAction(UsbManager.ACTION_USB_DEVICE_ATTACHED)
                addAction(UsbManager.ACTION_USB_DEVICE_DETACHED)
            }
            ContextCompat.registerReceiver(contexto, receptor, filtro, ContextCompat.RECEIVER_NOT_EXPORTED)
            receptorRegistrado = true
        }
        conectar()
        handler.removeCallbacks(buscarPuente)
        if (puertoPuente != null) handler.post(buscarPuente)
    }

    fun detener() {
        iniciada = false
        handler.removeCallbacks(reconectar)
        handler.removeCallbacks(buscarPuente)
        desconectar()
        if (receptorRegistrado) {
            try {
                contexto.unregisterReceiver(receptor)
            } catch (_: Exception) {
                // Ignorar
            }
            receptorRegistrado = false
        }
    }

    /**
     * true si el cable del Arduino está puesto, aunque la comunicación se haya cortado.
     * Sirve para saber si sacaron el teléfono de la estación o si solo hubo un error.
     */
    fun cablePuesto(): Boolean = try {
        UsbSerialProber.getDefaultProber().findAllDrivers(usb).isNotEmpty()
    } catch (_: Exception) {
        false
    }

    /** Busca un Arduino conectado; si no hay permiso, lo pide. */
    fun conectar() {
        if (conectado) return
        handler.removeCallbacks(reconectar)
        try {
            val driver = UsbSerialProber.getDefaultProber().findAllDrivers(usb).firstOrNull() ?: return
            if (!usb.hasPermission(driver.device)) {
                val banderas = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) PendingIntent.FLAG_MUTABLE else 0
                val permiso = PendingIntent.getBroadcast(
                    contexto, 0, Intent(ACCION_PERMISO).setPackage(contexto.packageName), banderas
                )
                usb.requestPermission(driver.device, permiso)
                return
            }
            val conexion = usb.openDevice(driver.device) ?: return
            val p = driver.ports.first()
            p.open(conexion)
            p.setParameters(115_200, 8, UsbSerialPort.STOPBITS_1, UsbSerialPort.PARITY_NONE)
            p.dtr = true
            puerto = p
            lector = SerialInputOutputManager(p, object : SerialInputOutputManager.Listener {
                override fun onNewData(data: ByteArray) {
                    handler.post { recibirBytes(data) }
                }

                override fun onRunError(e: Exception) {
                    handler.post { desconectarPorError() }
                }
            }).also { it.start() }
            conectado = true
            alCambiarEstado(true)
            // El Uno se reinicia al abrir el puerto: se le pide el saludo cuando ya arrancó.
            handler.postDelayed({ enviar(ProtocoloArduino.COMANDO_SALUDO) }, 2_500L)
        } catch (_: Exception) {
            desconectarPorError()
        }
    }

    /** La comunicación falló: se cierra el puerto y, si el cable sigue puesto, se reintenta. */
    private fun desconectarPorError() {
        desconectar()
        if (iniciada) {
            handler.removeCallbacks(reconectar)
            handler.postDelayed(reconectar, ESPERA_RECONEXION_MS)
        }
    }

    private val reconectar = object : Runnable {
        override fun run() {
            if (!iniciada || conectado || !cablePuesto()) return
            val driver = try {
                UsbSerialProber.getDefaultProber().findAllDrivers(usb).firstOrNull()
            } catch (_: Exception) {
                null
            }
            // Sin permiso no se insiste (pedirlo abriría un diálogo cada pocos segundos).
            if (driver != null && usb.hasPermission(driver.device)) conectar()
            if (!conectado) handler.postDelayed(this, ESPERA_RECONEXION_MS)
        }
    }

    fun desconectar() {
        val estaba = conectado
        conectado = false
        try {
            lector?.stop()
        } catch (_: Exception) {
            // Ignorar
        }
        lector = null
        try {
            puerto?.close()
        } catch (_: Exception) {
            // Ignorar
        }
        puerto = null
        puente?.let { cerrar(it) }
        puente = null
        pendiente.clear()
        if (estaba) alCambiarEstado(false)
    }

    fun enviar(comando: String) {
        Log.d(ETIQUETA_LOG, "-> ${comando.trim()}")
        val s = puente
        if (s != null && conectado) {
            val bytes = comando.toByteArray(Charsets.US_ASCII)
            // La red no se puede usar en el hilo principal. Si falla, se cierra y avisa el lector.
            escritorPuente.execute {
                try {
                    s.getOutputStream().apply { write(bytes); flush() }
                } catch (_: Exception) {
                    cerrar(s)
                }
            }
            return
        }
        val p = puerto ?: return
        try {
            p.write(comando.toByteArray(Charsets.US_ASCII), 200)
        } catch (_: Exception) {
            desconectarPorError()
        }
    }

    /** Para probar sin Arduino (solo en compilaciones de depuración). */
    fun simularLinea(linea: String) {
        ProtocoloArduino.interpretar(linea)?.let(alRecibir)
    }

    fun simularConexion(conectar: Boolean) {
        if (conectado == conectar) return
        conectado = conectar
        alCambiarEstado(conectar)
    }

    private fun recibirBytes(datos: ByteArray) {
        for (b in datos) {
            val c = b.toInt().toChar()
            if (c == '\n') {
                ProtocoloArduino.interpretar(pendiente.toString())?.let(alRecibir)
                pendiente.clear()
            } else if (c != '\r' && pendiente.length < 120) {
                pendiente.append(c)
            }
        }
    }

    // ---------- Puente con la computadora (solo pruebas) ----------

    /** Cada pocos segundos, si no hay Arduino, intenta llegar al puente de la computadora. */
    private val buscarPuente = object : Runnable {
        override fun run() {
            val puertoTcp = puertoPuente ?: return
            if (!iniciada) return
            if (!conectado && puente == null) abrirPuente(puertoTcp)
            handler.postDelayed(this, ESPERA_PUENTE_MS)
        }
    }

    private fun abrirPuente(puertoTcp: Int) {
        val s = Socket()
        puente = s
        thread(isDaemon = true, name = "EhriaPuente") {
            try {
                s.connect(InetSocketAddress("127.0.0.1", puertoTcp), 1_000)
                // El Arduino envía datos una vez por segundo: 5 s sin nada es que se cortó.
                s.soTimeout = 5_000
                val entrada = s.getInputStream()
                val buffer = ByteArray(256)
                while (true) {
                    val n = entrada.read(buffer)
                    if (n < 0) break
                    val datos = buffer.copyOf(n)
                    handler.post { alRecibirDelPuente(s, datos) }
                }
            } catch (_: Exception) {
                // No hay puente, se cerró o dejaron de llegar datos
            }
            cerrar(s)
            handler.post { alCerrarPuente(s) }
        }
    }

    private fun alRecibirDelPuente(s: Socket, datos: ByteArray) {
        if (puente !== s) return
        if (!conectado) {
            // Con "adb reverse" la conexión se acepta aunque en la computadora no haya nadie:
            // recién cuando llegan datos del Arduino cuenta como conectada.
            conectado = true
            alCambiarEstado(true)
            handler.postDelayed({ enviar(ProtocoloArduino.COMANDO_SALUDO) }, 2_500L)
        }
        recibirBytes(datos)
    }

    private fun alCerrarPuente(s: Socket) {
        if (puente !== s) return
        puente = null
        if (conectado) desconectar()
    }

    private fun cerrar(s: Socket) {
        try {
            s.close()
        } catch (_: Exception) {
            // Ignorar
        }
    }

    @Suppress("DEPRECATION")
    private fun dispositivoDe(intent: Intent): UsbDevice? =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            intent.getParcelableExtra(UsbManager.EXTRA_DEVICE, UsbDevice::class.java)
        } else intent.getParcelableExtra(UsbManager.EXTRA_DEVICE)

    companion object {
        private const val ESPERA_RECONEXION_MS = 2_000L
        private const val ESPERA_PUENTE_MS = 3_000L
        private const val ACCION_PERMISO = "com.example.detectormovimiento.PERMISO_USB"
        private const val ETIQUETA_LOG = "EhriaArduino"
    }
}
