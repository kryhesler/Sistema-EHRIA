# Estación EHRIA: Arduino Uno

El teléfono es la cabeza del robot y el Arduino Uno es el cuerpo. Se conectan con un cable USB OTG.

## Materiales

- Arduino Uno (original o clon con CH340)
- Sensor DHT11 o DHT22 (temperatura y humedad)
- Sensor ultrasónico HC-SR04
- Servo (por ejemplo SG90 o MG90S) para la puerta del botiquín
- 2 módulos de 4 LED direccionables (NeoPixel / WS2812): uno afuera (indicador de colores) y uno adentro (iluminación del botiquín)
- 2 resistencias de 330 Ω (línea de datos de los LED) y un condensador de 1000 µF
- Fuente de 5 V (2 A) para el servo y los LED
- Cable USB OTG (USB-C o micro-USB, según el teléfono, a USB-B del Uno)

## Conexiones

| Componente   | Pin del sensor | Arduino |
|--------------|----------------|---------|
| DHT          | DATA           | D2 (con resistencia de 10 kΩ a 5 V si el módulo no la trae) |
| HC-SR04      | TRIG / ECHO    | D9 / D10 |
| Servo        | señal          | D3 |
| LED exterior (4 NeoPixel) | DIN | D5 → 330 Ω → DIN; 5V y GND a la fuente externa |
| LED interior (4 NeoPixel) | DIN | D6 → 330 Ω → DIN; 5V y GND a la fuente externa |

Todos los GND van juntos: Arduino, sensores, servo, LED y fuente externa. El condensador de 1000 µF va entre 5V y GND de la fuente, cerca de los LED. Cada módulo usa su propio pin, así no hace falta encadenarlos. Si el módulo tiene la entrada marcada como "IN" o "DI", es la misma que DIN.

## Cargar el programa

1. Abrir `ehria_estacion/ehria_estacion.ino` en el Arduino IDE.
2. Instalar las librerías "DHT sensor library" (Adafruit), "Adafruit Unified Sensor" y "Adafruit NeoPixel".
3. Si el sensor es blanco (DHT22), cambiar `TIPO_DHT` a `DHT22`.
4. Ajustar `ANGULO_CERRADO` y `ANGULO_ABIERTO` según cómo quede montado el servo.
   Si los colores salen cambiados (rojo en vez de verde), cambiar `NEO_GRB` por `NEO_RGB`.
   El brillo se ajusta con `BRILLO_EXTERIOR` y `BRILLO_INTERIOR`.
5. Elegir la placa "Arduino Uno" y cargar.

## Alimentación (importante)

- El teléfono alimenta al Arduino por el cable OTG. Mientras actúa como "host" USB, **la mayoría de los teléfonos no se carga** por ese puerto. Para que la estación funcione día y noche, usar un cable OTG con entrada de carga (cable "Y") o un teléfono que acepte cargar y ser host a la vez.
- **El servo y los LED no deben alimentarse desde el Arduino.** El servo al moverse y los LED encendidos en blanco piden más corriente de la que da el USB del teléfono, y se reinicia todo. Usar una fuente de 5 V aparte con la tierra unida al Arduino.
- Cuando la puerta llega a su lugar, el programa suelta el servo: deja de consumir y no tiembla.

## Qué hace la app con el Arduino

- **Temperatura y humedad:** el robot las dice al saludar. También calcula la sensación térmica.
- **Ultrasónico:** si alguien se acerca a menos de 1.2 m, el robot despierta y saluda. Si nadie se acerca en 45 s, se vuelve a dormir.
- **Sismo:** el servo abre la puerta del botiquín, la luz interior se enciende de forma suave y el LED exterior se pone en rojo como baliza.
- **LED exterior (colores):**
  - verde que "respira" despacio: vigilando;
  - ámbar tenue fijo: detección en pausa;
  - amarillo pulsando rápido: verificando un movimiento;
  - rojo intenso, mitades alternadas: sismo.
- **LED interior:** luz blanca cálida mientras la puerta del botiquín está abierta.
- **Sacar el teléfono de la estación:** la app lo detecta, porque se desconecta el cable o porque el teléfono cambia de posición, y pasa al modo primeros auxilios. Al volver a colocarlo, regresa al modo estación.

## Probar sin Arduino (solo para desarrollo)

Con la app de depuración instalada, se puede simular el Arduino desde la computadora:

```
adb shell am broadcast -a com.example.detectormovimiento.SIMULAR_ARDUINO -p com.example.detectormovimiento --ez conectado true
adb shell am broadcast -a com.example.detectormovimiento.SIMULAR_ARDUINO -p com.example.detectormovimiento --es linea "S,27.5,70.0,80"
```
