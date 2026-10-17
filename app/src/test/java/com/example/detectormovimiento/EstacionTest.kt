package com.example.detectormovimiento

import com.example.detectormovimiento.DetectorSismico.Nivel
import com.example.detectormovimiento.HistorialRepositorio.RegistroSismo
import com.example.detectormovimiento.ProtocoloArduino.Mensaje
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class EstacionTest {

    // ---------- Protocolo con el Arduino ----------

    @Test
    fun protocolo_lecturaCompleta() {
        val m = ProtocoloArduino.interpretar("S,25.3,61.0,85.5") as Mensaje.Datos
        assertEquals(25.3f, m.lectura.temperaturaC!!, 0.01f)
        assertEquals(61.0f, m.lectura.humedad!!, 0.01f)
        assertEquals(85.5f, m.lectura.distanciaCm!!, 0.01f)
    }

    @Test
    fun protocolo_sensoresSinDato() {
        val m = ProtocoloArduino.interpretar("S,nan,nan,-1\r") as Mensaje.Datos
        assertNull(m.lectura.temperaturaC)
        assertNull(m.lectura.humedad)
        assertNull(m.lectura.distanciaCm)
    }

    @Test
    fun protocolo_otrosMensajesYBasura() {
        assertEquals(Mensaje.Hola("1"), ProtocoloArduino.interpretar("EHRIA,1"))
        assertEquals(Mensaje.Puerta(true), ProtocoloArduino.interpretar("PUERTA,1"))
        assertEquals(Mensaje.Puerta(false), ProtocoloArduino.interpretar("PUERTA,0"))
        assertNull(ProtocoloArduino.interpretar(""))
        assertNull(ProtocoloArduino.interpretar("S,25"))
        assertNull(ProtocoloArduino.interpretar("ruido@@"))
    }

    @Test
    fun protocolo_comandos() {
        assertEquals("P1\n", ProtocoloArduino.comandoPuerta(true))
        assertEquals("L0\n", ProtocoloArduino.comandoLuzInterior(false))
        assertEquals("ER\n", ProtocoloArduino.comandoLedExterior(ProtocoloArduino.ModoLed.ALERTA))
    }

    // ---------- Presencia (ultrasónico) ----------

    @Test
    fun presencia_llegaYSeVa() {
        val d = DetectorPresencia()
        assertNull(d.procesar(300f, 0))
        assertNull(d.procesar(90f, 250))
        assertNull(d.procesar(85f, 500))
        assertEquals(DetectorPresencia.Evento.LLEGO, d.procesar(80f, 750))
        // Se mueve un poco (dentro del margen) y luego se va.
        assertNull(d.procesar(140f, 5_000))
        assertNull(d.procesar(null, 20_000))
        assertEquals(DetectorPresencia.Evento.SE_FUE, d.procesar(null, 25_100))
        assertFalse(d.presente)
    }

    @Test
    fun presencia_unaLecturaSueltaNoDespierta() {
        val d = DetectorPresencia()
        var t = 0L
        for (dist in listOf(300f, 60f, 300f, 300f, 70f, 300f)) {
            assertNull(d.procesar(dist, t))
            t += 250
        }
        assertFalse(d.presente)
    }

    // ---------- Asistente ----------

    @Test
    fun indiceCalor_tablaNoaa() {
        // NOAA: 90 °F (32.2 °C) con 60 % de humedad se siente como 100 °F (37.8 °C).
        assertEquals(37.8, AsistenteEstacion.indiceCalor(32.22f, 60f), 1.0)
        // Templado: casi igual a la temperatura.
        assertEquals(25.0, AsistenteEstacion.indiceCalor(25f, 50f), 1.0)
        assertEquals("precaución extrema", AsistenteEstacion.riesgoCalor(37.8))
        assertNull(AsistenteEstacion.riesgoCalor(24.0))
    }

    @Test
    fun saludo_conDatosYSinSismos() {
        val texto = AsistenteEstacion.saludo(
            AsistenteEstacion.Datos(25.4f, 61f, horaDelDia = 9, sismosHoy = emptyList(), deteccionActiva = true)
        )
        assertTrue(texto.startsWith("¡Buenos días!"))
        assertTrue(texto.contains("25 grados"))
        assertTrue(texto.contains("61 por ciento"))
        assertTrue(texto.contains("no se ha registrado actividad sísmica"))
    }

    @Test
    fun saludo_conSismosYCalor() {
        val sismo = RegistroSismo(1, 1_000, Nivel.MODERADO, 0.3f, 12, pgaPicoBruto = 0.35f)
        val texto = AsistenteEstacion.saludo(
            AsistenteEstacion.Datos(34f, 70f, horaDelDia = 15, sismosHoy = listOf(sismo), deteccionActiva = true)
        )
        assertTrue(texto.startsWith("¡Buenas tardes!"))
        assertTrue(texto.contains("Hace un calor peligroso"))
        assertTrue(texto.contains("Hoy se registró un sismo, el último de magnitud aproximada 5,6"))
    }

    @Test
    fun saludo_sinArduinoNiDeteccion() {
        val texto = AsistenteEstacion.saludo(
            AsistenteEstacion.Datos(null, null, horaDelDia = 22, sismosHoy = emptyList(), deteccionActiva = false)
        )
        assertTrue(texto.startsWith("¡Buenas noches!"))
        assertFalse(texto.contains("grados"))
        assertTrue(texto.contains("detección de sismos está apagada"))
    }
}
