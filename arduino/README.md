# Estación EHRIA: Arduino Uno

El teléfono es la cabeza del robot y el Arduino Uno es el cuerpo. Se conectan con un cable USB OTG.

## Materiales

- Arduino Uno (original o clon con CH340)
- Sensor DHT11 o DHT22 (temperatura y humedad)
- Sensor ultrasónico HC-SR04
- Servo (por ejemplo SG90 o MG90S) para la puerta del botiquín
- 2 LED con resistencias de 220 Ω: uno afuera (indicador) y uno adentro (iluminación)
- Fuente de 5 V para el servo
- Cable USB OTG (USB-C o micro-USB, según el teléfono, a USB-B del Uno)

## Conexiones

| Componente   | Pin del sensor | Arduino |
|--------------|----------------|---------|
| DHT          | DATA           | D2 (con resistencia de 10 kΩ a 5 V si el módulo no la trae) |
| HC-SR04      | TRIG / ECHO    | D9 / D10 |
| Servo        | señal          | D3 |
| LED exterior | ánodo (+)      | D5 → 220 Ω → LED → GND |
| LED interior | ánodo (+)      | D6 → 220 Ω → LED → GND |

Todos los GND van juntos: Arduino, sensores, servo y fuente del servo.

## Cargar el programa

1. Abrir `ehria_estacion/ehria_estacion.ino` en el Arduino IDE.
2. Instalar las librerías "DHT sensor library" (Adafruit) y "Adafruit Unified Sensor".
3. Si el sensor es blanco (DHT22), cambiar `TIPO_DHT` a `DHT22`.
4. Ajustar `ANGULO_CERRADO` y `ANGULO_ABIERTO` según cómo quede montado el servo.
5. Elegir la placa "Arduino Uno" y cargar.

## Alimentación (importante)

- El teléfono alimenta al Arduino por el cable OTG. Mientras actúa como "host" USB, **la mayoría de los teléfonos no se carga** por ese puerto. Para que la estación funcione día y noche, usar un cable OTG con entrada de carga (cable "Y") o un teléfono que acepte cargar y ser host a la vez.
- **El servo no debe alimentarse desde el Arduino.** Al moverse pide más corriente de la que da el USB del teléfono, y se reinicia todo. Usar una fuente de 5 V aparte con la tierra unida al Arduino.

## Qué hace la app con el Arduino

- **Temperatura y humedad:** el robot las dice al saludar. También calcula la sensación térmica.
- **Ultrasónico:** si alguien se acerca a menos de 1.2 m, el robot despierta y saluda. Si nadie se acerca en 45 s, se vuelve a dormir.
- **Sismo:** el servo abre la puerta del botiquín, se enciende la luz interior y el LED exterior parpadea muy rápido.
- **LED exterior:**
  - un destello cada 3 s: vigilando;
  - parpadeo lento: detección en pausa;
  - parpadeo rápido: evaluando un movimiento;
  - parpadeo muy rápido: sismo.
- **Sacar el teléfono de la estación:** la app lo detecta, porque se desconecta el cable o porque el teléfono cambia de posición, y pasa al modo primeros auxilios. Al volver a colocarlo, regresa al modo estación.

## Probar sin Arduino (solo para desarrollo)

Con la app de depuración instalada, se puede simular el Arduino desde la computadora:

```
adb shell am broadcast -a com.example.detectormovimiento.SIMULAR_ARDUINO -p com.example.detectormovimiento --ez conectado true
adb shell am broadcast -a com.example.detectormovimiento.SIMULAR_ARDUINO -p com.example.detectormovimiento --es linea "S,27.5,70.0,80"
```
