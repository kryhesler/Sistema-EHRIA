package com.example.detectormovimiento

import com.example.detectormovimiento.DetectorSismico.Nivel
import com.example.detectormovimiento.HistorialRepositorio.RegistroMedicion
import com.example.detectormovimiento.HistorialRepositorio.RegistroSismo
import com.example.detectormovimiento.HistorialRepositorio.TipoMedicion
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

class HistorialRepositorioTest {

    @get:Rule
    val carpeta = TemporaryFolder()

    @Test
    fun guardaYLeeSismos_masRecientePrimero() {
        val repo = HistorialRepositorio(carpeta.root)
        repo.guardarSismo(RegistroSismo(1, 1_000, Nivel.LEVE, 0.1f, 11))
        repo.guardarSismo(RegistroSismo(2, 5_000, Nivel.FUERTE, 1.8f, 25, pgaPicoBruto = 2.6f))

        val sismos = HistorialRepositorio(carpeta.root).sismos()
        assertEquals(listOf(2L, 1L), sismos.map { it.id })
        assertEquals(Nivel.FUERTE, sismos[0].nivel)
        assertEquals(1.8f, sismos[0].pgaPico, 0.001f)
        assertEquals(2.6f, sismos[0].pgaPicoBruto, 0.001f)
        // Un registro guardado antes de este campo usa el pico filtrado.
        assertEquals(0.1f, sismos[1].pgaPicoBruto, 0.001f)
    }

    @Test
    fun mismoId_actualizaEnVezDeDuplicar() {
        val repo = HistorialRepositorio(carpeta.root)
        repo.guardarSismo(RegistroSismo(7, 1_000, Nivel.LEVE, 0.2f, 10))
        repo.guardarSismo(RegistroSismo(7, 1_000, Nivel.MODERADO, 0.6f, 18))

        val sismos = repo.sismos()
        assertEquals(1, sismos.size)
        assertEquals(Nivel.MODERADO, sismos[0].nivel)
        assertEquals(18, sismos[0].duracionS)
    }

    @Test
    fun medicionesConValoresVacios() {
        val repo = HistorialRepositorio(carpeta.root)
        repo.guardarMedicion(RegistroMedicion(3, 3_000, TipoMedicion.PULSO, null, null, "Pulso\tno detectado", 4))

        val m = repo.mediciones().single()
        assertEquals(TipoMedicion.PULSO, m.tipo)
        assertNull(m.rpm)
        assertNull(m.bpm)
        assertEquals("Pulso no detectado", m.resultado)
        assertEquals(4, m.intentosPulso)
    }

    @Test
    fun registrosViejosSinTipo_seLeen() {
        File(carpeta.root, "historial_mediciones.tsv").writeText("1\t1000\t\t72\tCompleta\t0\n2\t2000\t15\t\tParcial\t0\n")
        val tipos = HistorialRepositorio(carpeta.root).mediciones().map { it.tipo }
        assertEquals(listOf(TipoMedicion.RESPIRACION, TipoMedicion.PULSO), tipos)
    }

    @Test
    fun lineasDanadas_seIgnoran() {
        File(carpeta.root, "historial_sismos.tsv").writeText("basura\n1\t2\tNO_EXISTE\t0.1\t3\n4\t5000\tLEVE\t0.1\t12\n")
        val sismos = HistorialRepositorio(carpeta.root).sismos()
        assertEquals(listOf(4L), sismos.map { it.id })
    }

    @Test
    fun limiteDeRegistros_yBorrarTodo() {
        val repo = HistorialRepositorio(carpeta.root)
        for (i in 1..(HistorialRepositorio.MAXIMO + 5)) {
            repo.guardarMedicion(RegistroMedicion(i.toLong(), i * 1_000L, TipoMedicion.RESPIRACION, 14, null, "Eupnea", 0))
        }
        assertEquals(HistorialRepositorio.MAXIMO, repo.mediciones().size)
        repo.borrarTodo()
        assertTrue(repo.mediciones().isEmpty())
        assertTrue(repo.sismos().isEmpty())
    }
}
