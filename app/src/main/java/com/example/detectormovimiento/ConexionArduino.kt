package com.example.detectormovimiento

import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.hardware.usb.UsbManager
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.util.Log
import androidx.core.content.ContextCompat
import com.hoho.android.usbserial.driver.UsbSerialPort
import com.hoho.android.usbserial.driver.UsbSerialProber
import com.hoho.android.usbserial.util.SerialInputOutputManager

/**
 * Conexión por cable USB con el Arduino Uno de la estación (original o clon con CH340).
 *
 * - Pide permiso al usuario la primera vez que se conecta.
 * - Lee línea por línea en un hilo aparte y entrega los mensajes en el hilo principal.
 * - Avisa cuando el cable se conecta o se desconecta (por ejemplo, al sacar la "cabeza").
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

    private val handler = Handler(Looper.getMainLooper())
    private val usb = contexto.getSystemService(Context.USB_SERVICE) as UsbManager
    private var puerto: UsbSerialPort? = null
    private var lector: SerialInputOutputManager? = null
    private val pendiente = StringBuilder()
    private var receptorRegistrado = false

    private val receptor = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            when (intent.action) {
                ACCION_PERMISO -> if (intent.getBooleanExtra(UsbManager.EXTRA_PERMISSION_GRANTED, false)) conectar()
                UsbManager.ACTION_USB_DEVICE_ATTACHED -> conectar()
                UsbManager.ACTION_USB_DEVICE_DETACHED -> desconectar()
            }
        }
    }

    fun iniciar() {
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
    }

    fun detener() {
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

    /** Busca un Arduino conectado; si no hay permiso, lo pide. */
    fun conectar() {
        if (conectado) return
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
                    handler.post { desconectar() }
                }
            }).also { it.start() }
            conectado = true
            alCambiarEstado(true)
            // El Uno se reinicia al abrir el puerto: se le pide el saludo cuando ya arrancó.
            handler.postDelayed({ enviar(ProtocoloArduino.COMANDO_SALUDO) }, 2_500L)
        } catch (_: Exception) {
            desconectar()
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
        pendiente.clear()
        if (estaba) alCambiarEstado(false)
    }

    fun enviar(comando: String) {
        Log.d(ETIQUETA_LOG, "-> ${comando.trim()}")
        val p = puerto ?: return
        try {
            p.write(comando.toByteArray(Charsets.US_ASCII), 200)
        } catch (_: Exception) {
            desconectar()
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

    companion object {
        private const val ACCION_PERMISO = "com.example.detectormovimiento.PERMISO_USB"
        private const val ETIQUETA_LOG = "EhriaArduino"
    }
}
