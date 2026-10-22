/*
  EHRIA - Estación comunitaria (Arduino Uno)

  Lee temperatura y humedad (DHT), mueve el servo de la puerta del botiquín y controla un
  módulo de 4 LED direccionables (NeoPixel / WS2812) que ilumina el botiquín por dentro.
  Se comunica con el teléfono por el cable USB a 115200 baudios. El protocolo está descrito
  en app/src/main/java/com/example/detectormovimiento/ProtocoloArduino.kt

  Librerías (Arduino IDE > Herramientas > Administrar bibliotecas):
    - "DHT sensor library" de Adafruit (instala también "Adafruit Unified Sensor")
    - "Adafruit NeoPixel"
    - "Servo" (viene incluida)

  Conexiones:
    DHT11/DHT22   DATA -> D2      VCC -> 5V   GND -> GND  (resistencia de 10k entre DATA y 5V
                                                            si el módulo no la trae)
    Servo         señal -> D3     rojo -> 5V EXTERNO (no del Arduino)  marrón -> GND común
    NeoPixel int. DIN -> resistencia 330 ohm -> D6   5V -> 5V EXTERNO   GND -> GND común
    (condensador de 1000 uF entre 5V y GND de la fuente externa, cerca de los LED)
*/

#include <Adafruit_NeoPixel.h>
#include <DHT.h>
#include <Servo.h>

// ---------- Configuración ----------
#define PIN_DHT 2
#define TIPO_DHT DHT11          // cambiar a DHT22 si el sensor es blanco (DHT22 / AM2302)
#define PIN_SERVO 3
#define PIN_LED_INTERIOR 6
#define LEDS_INTERIOR 4

const int ANGULO_CERRADO = 0;    // ajustar según cómo quede montado el servo
const int ANGULO_ABIERTO = 95;
const unsigned long CADA_LECTURA_MS = 1000;  // envío de datos al teléfono
const unsigned long CADA_DHT_MS = 2000;      // el DHT no se puede leer más rápido
const unsigned long SERVO_REPOSO_MS = 400;   // tras llegar, se suelta el servo (no tiembla)
const uint8_t BRILLO_INTERIOR = 170;         // luz blanca cálida para ver el botiquín
const char *VERSION = "3";

DHT dht(PIN_DHT, TIPO_DHT);
Servo servo;
Adafruit_NeoPixel ledInterior(LEDS_INTERIOR, PIN_LED_INTERIOR, NEO_GRB + NEO_KHZ800);

float temperatura = NAN;
float humedad = NAN;
unsigned long ultimaLectura = 0;
unsigned long ultimoDht = 0;

// Puerta: el servo se mueve de a poco para no dar un golpe
int anguloActual = ANGULO_CERRADO;
int anguloDestino = ANGULO_CERRADO;
unsigned long ultimoPasoServo = 0;
bool avisarPuerta = false;

bool luzInterior = false;
uint8_t nivelInterior = 0;          // sube y baja de a poco (encendido suave)
unsigned long ultimoCuadroLed = 0;

String linea = "";

void setup() {
  Serial.begin(115200);
  ledInterior.begin();
  ledInterior.clear();
  ledInterior.show();
  dht.begin();
  servo.attach(PIN_SERVO);
  servo.write(ANGULO_CERRADO);
  saludar();
}

void loop() {
  unsigned long ahora = millis();

  leerComandos();
  moverServo(ahora);
  actualizarLuz(ahora);

  if (ahora - ultimoDht >= CADA_DHT_MS) {
    ultimoDht = ahora;
    float t = dht.readTemperature();
    float h = dht.readHumidity();
    if (!isnan(t)) temperatura = t;
    if (!isnan(h)) humedad = h;
  }

  if (ahora - ultimaLectura >= CADA_LECTURA_MS) {
    ultimaLectura = ahora;
    Serial.print("S,");
    imprimirNumero(temperatura);
    Serial.print(',');
    imprimirNumero(humedad);
    Serial.println();
  }
}

void saludar() {
  Serial.print("EHRIA,");
  Serial.println(VERSION);
}

void imprimirNumero(float valor) {
  if (isnan(valor)) Serial.print("nan");
  else Serial.print(valor, 1);
}

void leerComandos() {
  while (Serial.available() > 0) {
    char c = (char)Serial.read();
    if (c == '\n' || c == '\r') {
      if (linea.length() > 0) ejecutar(linea);
      linea = "";
    } else if (linea.length() < 16) {
      linea += c;
    }
  }
}

void ejecutar(const String &cmd) {
  char tipo = cmd.charAt(0);
  char valor = cmd.length() > 1 ? cmd.charAt(1) : ' ';
  switch (tipo) {
    case 'P':  // puerta del botiquín
      anguloDestino = (valor == '1') ? ANGULO_ABIERTO : ANGULO_CERRADO;
      avisarPuerta = true;
      if (!servo.attached()) {
        servo.write(anguloActual);
        servo.attach(PIN_SERVO);
      }
      break;
    case 'L':  // luz interior
      luzInterior = (valor == '1');
      break;
    case 'H':
      saludar();
      break;
  }
}

void moverServo(unsigned long ahora) {
  if (anguloActual == anguloDestino) {
    if (avisarPuerta) {
      avisarPuerta = false;
      Serial.print("PUERTA,");
      Serial.println(anguloActual == ANGULO_ABIERTO ? 1 : 0);
    }
    // Quieto, se suelta el servo: no consume ni tiembla con los LED.
    if (servo.attached() && ahora - ultimoPasoServo >= SERVO_REPOSO_MS) servo.detach();
    return;
  }
  if (ahora - ultimoPasoServo < 12) return;   // ~1 s de recorrido completo
  ultimoPasoServo = ahora;
  anguloActual += (anguloDestino > anguloActual) ? 1 : -1;
  servo.write(anguloActual);
}

// ---------- Luz interior (LED direccionables) ----------

// Se enciende y apaga de forma suave. Mientras el servo se mueve no se tocan los LED
// (enviarles datos pausa las interrupciones un instante y la puerta temblaría).
void actualizarLuz(unsigned long ahora) {
  if (ahora - ultimoCuadroLed < 20) return;        // 50 cuadros por segundo
  if (servo.attached() && anguloActual != anguloDestino) return;
  ultimoCuadroLed = ahora;

  uint8_t destino = luzInterior ? BRILLO_INTERIOR : 0;
  if (nivelInterior == destino) return;
  int siguiente = nivelInterior < destino ? (int)nivelInterior + 10 : (int)nivelInterior - 10;
  nivelInterior = nivelInterior < destino ? (uint8_t)min(siguiente, (int)destino) : (uint8_t)max(siguiente, (int)destino);
  uint32_t color = ledInterior.Color((uint16_t)255 * nivelInterior / 255, (uint16_t)200 * nivelInterior / 255,
                                     (uint16_t)140 * nivelInterior / 255);
  for (uint8_t i = 0; i < LEDS_INTERIOR; i++) ledInterior.setPixelColor(i, color);
  ledInterior.show();
}
