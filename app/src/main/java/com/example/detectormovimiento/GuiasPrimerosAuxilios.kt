package com.example.detectormovimiento

/**
 * Guías que el robot dice paso a paso cuando se las piden por voz. Siguen las recomendaciones
 * para personas sin formación médica (AHA, Cruz Roja y "Stop the Bleed"). Son orientativas:
 * no reemplazan a un curso de primeros auxilios ni al personal de salud.
 */
object GuiasPrimerosAuxilios {

    /** [metronomo]: al terminar los pasos suena el ritmo de las compresiones (RCP). */
    data class Guia(val titulo: String, val pasos: List<String>, val metronomo: Boolean = false)

    /** Ritmo de las compresiones: entre 100 y 120 por minuto. */
    const val RITMO_RCP = 110

    val RCP = Guia(
        "RCP · Reanimación",
        listOf(
            "Primero, asegúrate de que el lugar sea seguro para ti y para la persona.",
            "Tócale los hombros y pregúntale en voz alta si está bien. Si no responde y no respira, o solo jadea, empieza la RCP.",
            "Pide a alguien que llame a emergencias y que traiga un desfibrilador si hay uno cerca.",
            "Acuesta a la persona boca arriba sobre el suelo y arrodíllate a su lado.",
            "Pon el talón de una mano en el centro del pecho, la otra mano encima, y entrelaza los dedos.",
            "Con los brazos rectos, empuja fuerte y rápido, unos 5 centímetros, y deja que el pecho vuelva a subir. Sigue mi sonido y no pares hasta que llegue ayuda.",
        ),
        metronomo = true,
    )

    val TORNIQUETE = Guia(
        "Torniquete · Sangrado grave",
        listOf(
            "Si hay guantes en el botiquín, póntelos.",
            "Presiona la herida con fuerza usando una gasa o una tela limpia. Si la sangre no para y la herida está en un brazo o una pierna, usa un torniquete.",
            "Colócalo de 5 a 8 centímetros más arriba de la herida, más cerca del cuerpo. No lo pongas sobre el codo ni la rodilla: si la herida está ahí, colócalo más arriba.",
            "Si lo improvisas, usa una tela de al menos 4 centímetros de ancho y un palo como varilla. Nunca uses un cordón ni un cable.",
            "Aprieta la banda y gira la varilla hasta que la sangre deje de salir. Luego fija la varilla para que no se suelte. Va a doler: es normal.",
            "Anota la hora en que lo pusiste. No lo aflojes ni lo quites: eso lo hace el personal de salud. Llama a emergencias y no dejes sola a la persona.",
        ),
    )

    val ATRAGANTAMIENTO = Guia(
        "Atragantamiento",
        listOf(
            "Esta guía es para adultos y niños mayores de un año. Si la persona puede toser o hablar, anímala a toser con fuerza.",
            "Si no puede toser, hablar ni respirar, inclínala hacia adelante y dale 5 golpes firmes en la espalda, entre los omóplatos, con el talón de la mano.",
            "Si no sale, ponte detrás de ella, rodéala con los brazos y pon un puño cerrado arriba del ombligo, cubierto con la otra mano.",
            "Empuja con fuerza hacia adentro y hacia arriba, 5 veces.",
            "Alterna 5 golpes en la espalda y 5 compresiones hasta que salga el objeto.",
            "Si pierde el conocimiento, acuéstala en el suelo, pide que llamen a emergencias y empieza la RCP.",
        ),
    )

    val QUEMADURA = Guia(
        "Quemaduras",
        listOf(
            "Aleja a la persona de lo que la quemó, sin ponerte en peligro.",
            "Enfría la quemadura con agua corriente fresca, no helada, durante 20 minutos.",
            "Quita anillos, relojes o ropa cerca de la quemadura antes de que se hinche, pero no despegues la ropa pegada a la piel.",
            "No pongas hielo, pasta dental, mantequilla ni cremas, y no revientes las ampollas.",
            "Cúbrela sin apretar con una gasa limpia o con plástico de cocina.",
            "Busca atención médica si es grande o profunda, o si está en la cara, las manos, los pies o los genitales.",
        ),
    )

    /**
     * Qué hay en el botiquín y para qué sirve cada cosa.
     * TEXTO PROVISIONAL: reemplazar por el contenido real del botiquín de la estación.
     */
    val BOTIQUIN = Guia(
        "Mi botiquín",
        listOf(
            "Guantes desechables: póntelos antes de tocar una herida o sangre.",
            "Gasas estériles: para limpiar y cubrir heridas, y para presionar si sangran.",
            "Vendas: sujetan las gasas o inmovilizan un golpe. Que no queden tan apretadas que los dedos se pongan fríos o morados.",
            "Esparadrapo y curitas: para cerrar y cubrir heridas pequeñas.",
            "Suero fisiológico: para lavar heridas y ojos.",
            "Tijeras de punta redonda: para cortar gasas, vendas o la ropa sobre una herida.",
            "Torniquete: solo para un sangrado grave en un brazo o una pierna que no para al presionar. Pídeme la guía del torniquete.",
            "Manta térmica: abriga a una persona en shock o con frío. El lado dorado va hacia afuera.",
        ),
    )
}
