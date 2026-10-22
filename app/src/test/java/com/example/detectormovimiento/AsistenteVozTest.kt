package com.example.detectormovimiento

import com.example.detectormovimiento.ComandosVoz.Comando
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AsistenteVozTest {

    // ---------- Nombre del robot ----------

    @Test
    fun nombre_variantesDelReconocedor() {
        for (frase in listOf("Ehria abre el botiquín", "Ería, abre el botiquín", "eh ria abre el botiquin", "Hería abre el botiquín")) {
            val e = ComandosVoz.escuchar(frase)
            assertTrue(frase, e.llamado)
            assertEquals(frase, "abre el botiquin", e.resto)
        }
    }

    @Test
    fun nombre_alFinalOSolo() {
        assertEquals("hola", ComandosVoz.escuchar("Hola, Ehria").resto)
        assertEquals("abre el botiquin", ComandosVoz.escuchar("abre el botiquín, Ehria").resto)
        val solo = ComandosVoz.escuchar("¡Ehria!")
        assertTrue(solo.llamado)
        assertEquals("", solo.resto)
    }

    @Test
    fun nombre_sinNombreNoLlama() {
        val e = ComandosVoz.escuchar("abre el botiquín")
        assertFalse(e.llamado)
        assertFalse(ComandosVoz.escuchar("la maestra dijo que hoy hay examen").llamado)
    }

    // ---------- Órdenes ----------

    @Test
    fun ordenes_botiquin() {
        assertEquals(Comando.ABRIR_BOTIQUIN, ComandosVoz.interpretar("abre el botiquín"))
        assertEquals(Comando.ABRIR_BOTIQUIN, ComandosVoz.interpretar("abre la puerta"))
        assertEquals(Comando.CERRAR_BOTIQUIN, ComandosVoz.interpretar("cierra el botiquín"))
        assertEquals(Comando.CONTENIDO_BOTIQUIN, ComandosVoz.interpretar("¿qué tiene el botiquín?"))
        assertEquals(Comando.CONTENIDO_BOTIQUIN, ComandosVoz.interpretar("cómo se usa el botiquín"))
    }

    @Test
    fun ordenes_primerosAuxilios() {
        assertEquals(Comando.RCP, ComandosVoz.interpretar("¿cómo hago RCP?"))
        assertEquals(Comando.RCP, ComandosVoz.interpretar("una persona no respira"))
        assertEquals(Comando.TORNIQUETE, ComandosVoz.interpretar("cómo pongo un torniquete"))
        assertEquals(Comando.TORNIQUETE, ComandosVoz.interpretar("está sangrando mucho"))
        assertEquals(Comando.ATRAGANTAMIENTO, ComandosVoz.interpretar("alguien se está atragantando"))
        assertEquals(Comando.QUEMADURA, ComandosVoz.interpretar("se quemó la mano"))
        assertEquals(Comando.AMBULANCIA, ComandosVoz.interpretar("llama a una ambulancia"))
        assertEquals(Comando.AMBULANCIA, ComandosVoz.interpretar("llama al 128"))
    }

    @Test
    fun ordenes_informacion() {
        assertEquals(Comando.PUNTO_ENCUENTRO, ComandosVoz.interpretar("¿dónde está el punto de encuentro?"))
        assertEquals(Comando.PUNTO_ENCUENTRO, ComandosVoz.interpretar("a dónde evacuamos"))
        assertEquals(Comando.CLIMA, ComandosVoz.interpretar("¿qué temperatura hace?"))
        assertEquals(Comando.CLIMA, ComandosVoz.interpretar("cuánta humedad hay"))
        assertEquals(Comando.SISMOS, ComandosVoz.interpretar("¿ha temblado hoy?"))
        assertEquals(Comando.HORA, ComandosVoz.interpretar("qué hora es"))
        assertEquals(Comando.PRESENTARSE, ComandosVoz.interpretar("preséntate"))
        assertEquals(Comando.PRESENTARSE, ComandosVoz.interpretar("¿cómo te llamas?"))
        assertEquals(Comando.AYUDA, ComandosVoz.interpretar("¿qué puedes hacer?"))
        assertEquals(Comando.GRACIAS, ComandosVoz.interpretar("muchas gracias"))
    }

    @Test
    fun ordenes_controlesSoloSiSonCortos() {
        assertEquals(Comando.DETENER, ComandosVoz.interpretar("para"))
        assertEquals(Comando.DETENER, ComandosVoz.interpretar("ya basta"))
        assertEquals(Comando.SIGUIENTE, ComandosVoz.interpretar("siguiente"))
        assertEquals(Comando.ANTERIOR, ComandosVoz.interpretar("el anterior"))
        assertEquals(Comando.REPETIR, ComandosVoz.interpretar("repite"))
        // "para" dentro de una pregunta no es "detente".
        assertEquals(Comando.CONTENIDO_BOTIQUIN, ComandosVoz.interpretar("para qué sirve lo del botiquín"))
        assertNull(ComandosVoz.interpretar("mañana vamos a la playa"))
        assertNull(ComandosVoz.interpretar(""))
    }

    // ---------- Punto de encuentro ----------

    @Test
    fun puntoEncuentro_descripcion() {
        val p = PuntoEncuentro("la cancha de fútbol", PuntoEncuentro.Direccion.DERECHA, 80, "Sal por el pasillo principal")
        assertEquals("A unos 80 metros, a tu derecha", p.ubicacion())
        val voz = p.descripcionVoz()
        assertTrue(voz.startsWith("El punto de encuentro es la cancha de fútbol. Queda a unos 80 metros, a tu derecha."))
        assertTrue(voz.contains("Sal por el pasillo principal."))
        assertTrue(voz.endsWith("sin empujar."))
    }

    @Test
    fun puntoEncuentro_sinDistancia() {
        val p = PuntoEncuentro("el patio", PuntoEncuentro.Direccion.ATRAS, null, "")
        assertEquals("Detrás de ti", p.ubicacion())
        assertFalse(p.descripcionVoz().contains("  "))
    }

    // ---------- Respuestas ----------

    @Test
    fun respuestas_climaYHora() {
        assertTrue(AsistenteEstacion.respuestaClima(34f, 70f).contains("calor peligroso"))
        assertTrue(AsistenteEstacion.respuestaClima(null, null).contains("Todavía no tengo datos"))
        assertEquals("Son las 3 y media de la tarde.", AsistenteEstacion.hora(15, 30))
        assertEquals("Es la una de la noche.", AsistenteEstacion.hora(1, 0))
        assertEquals("Son las 12 y 5 de la tarde.", AsistenteEstacion.hora(12, 5))
    }

    @Test
    fun guias_tienenPasos() {
        for (g in listOf(
            GuiasPrimerosAuxilios.RCP, GuiasPrimerosAuxilios.TORNIQUETE, GuiasPrimerosAuxilios.ATRAGANTAMIENTO,
            GuiasPrimerosAuxilios.QUEMADURA, GuiasPrimerosAuxilios.BOTIQUIN,
        )) {
            assertTrue(g.titulo, g.pasos.size >= 4)
        }
        assertTrue(GuiasPrimerosAuxilios.RCP.metronomo)
        assertTrue(GuiasPrimerosAuxilios.RITMO_RCP in 100..120)
    }

    @Test
    fun metronomo_duraUnPeriodoExacto() {
        val pcm = SintetizadorSonidos.metronomo(110)
        assertTrue(kotlin.math.abs(60_000L / 110 - SintetizadorSonidos.duracionMs(pcm)) <= 2)
    }
}
