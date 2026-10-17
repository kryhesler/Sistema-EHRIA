/*
  EHRIA - Estación comunitaria (Arduino Uno)

  Lee temperatura y humedad (DHT), la distancia a una persona (ultrasónico HC-SR04),
  mueve el servo de la puerta del botiquín y controla dos módulos de 4 LED direccionables
  (NeoPixel / WS2812): uno afuera como indicador de colores y otro adentro para iluminar.
  Se comunica con el teléfono por el cable USB a 115200 baudios. El protocolo está descrito
  en app/src/main/java/com/example/detectormovimiento/ProtocoloArduino.kt

  Librerías (Arduino IDE > Herramientas > Administrar bibliotecas):
    - "DHT sensor library" de Adafruit (instala también "Adafruit Unified Sensor")
    - "Adafruit NeoPixel"
    - "Servo" (viene incluida)

  Conexiones:
    DHT11/DHT22   DATA -> D2      VCC -> 5V   GND -> GND  (resistencia de 10k entre DATA y 5V
                                                            si el módulo no la trae)
    HC-SR04       TRIG -> D9      ECHO -> D10 VCC -> 5V   GND -> GND
    Servo         señal -> D3     rojo -> 5V EXTERNO (no del Arduino)  marrón -> GND común
    NeoPixel ext. DIN -> resistencia 330 ohm -> D5   5V -> 5V EXTERNO   GND -> GND común
    NeoPixel int. DIN -> resistencia 330 ohm -> D6   5V -> 5V EXTERNO   GND -> GND común
    (condensador de 1000 uF entre 5V y GND de la fuente externa, cerca de los LED)
*/

#include <Adafruit_NeoPixel.h>
#include <DHT.h>
#include <Servo.h>

// ---------- Configuración ----------
#define PIN_DHT 2
#define TIPO_DHT DHT11          // cambiar a DHT22 si el sensor es blanco (DHT22 / AM2302)
#define PIN_TRIG 9
#define PIN_ECHO 10
#define PIN_SERVO 3
#define PIN_LED_EXTERIOR 5
#define PIN_LED_INTERIOR 6
#define LEDS_POR_MODULO 4

const int ANGULO_CERRADO = 0;    // ajustar según cómo quede montado el servo
const int ANGULO_ABIERTO = 95;
const unsigned long CADA_LECTURA_MS = 250;   // envío de datos al teléfono
const unsigned long CADA_DHT_MS = 2000;      // el DHT no se puede leer más rápido
const unsigned long SERVO_REPOSO_MS = 400;   // tras llegar, se suelta el servo (no tiembla)
const uint8_t BRILLO_EXTERIOR = 70;          // 0-255: se ve de lejos sin encandilar
const uint8_t BRILLO_INTERIOR = 170;         // luz blanca cálida para ver el botiquín
const char *VERSION = "2";

DHT dht(PIN_DHT, TIPO_DHT);
Servo servo;
Adafruit_NeoPixel ledExterior(LEDS_POR_MODULO, PIN_LED_EXTERIOR, NEO_GRB + NEO_KHZ800);
Adafruit_NeoPixel ledInterior(LEDS_POR_MODULO, PIN_LED_INTERIOR, NEO_GRB + NEO_KHZ800);

float temperatura = NAN;
float humedad = NAN;
unsigned long ultimaLectura = 0;
unsigned long ultimoDht = 0;

// Puerta: el servo se mueve de a poco para no dar un golpe
int anguloActual = ANGULO_CERRADO;
int anguloDestino = ANGULO_CERRADO;
unsigned long ultimoPasoServo = 0;
bool avisarPuerta = false;

// LED exterior: '0' apagado, 'N' normal, 'P' pausa, 'A' aviso, 'R' alerta
char modoLed = 'P';
bool luzInterior = false;
uint8_t nivelInterior = 0;          // sube y baja de a poco (encendido suave)
unsigned long ultimoCuadroLed = 0;

String linea = "";

void setup() {
  Serial.begin(115200);
  pinMode(PIN_TRIG, OUTPUT);
  pinMode(PIN_ECHO, INPUT);
  ledExterior.begin();
  ledInterior.begin();
  ledExterior.clear();
  ledInterior.clear();
  ledExterior.show();
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
  actualizarLeds(ahora);

  if (ahora - ultimoDht >= CADA_DHT_MS) {
    ultimoDht = ahora;
    float t = dht.readTemperature();
    float h = dht.readHumidity();
    if (!isnan(t)) temperatura = t;
    if (!isnan(h)) humedad = h;
  }

  if (ahora - ultimaLectura >= CADA_LECTURA_MS) {
    ultimaLectura = ahora;
    float distancia = medirDistancia();
    Serial.print("S,");
    imprimirNumero(temperatura);
    Serial.print(',');
    imprimirNumero(humedad);
    Serial.print(',');
    Serial.println(distancia, 1);
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

// Distancia en cm, o -1 si no hay eco (nadie enfrente o fuera de alcance)
float medirDistancia() {
  digitalWrite(PIN_TRIG, LOW);
  delayMicroseconds(2);
  digitalWrite(PIN_TRIG, HIGH);
  delayMicroseconds(10);
  digitalWrite(PIN_TRIG, LOW);
  unsigned long duracion = pulseIn(PIN_ECHO, HIGH, 25000UL);  // máximo ~4 m
  if (duracion == 0) return -1;
  return duracion / 58.0;
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
    case 'E':  // modo del LED exterior
      if (valor == '0' || valor == 'N' || valor == 'P' || valor == 'A' || valor == 'R') modoLed = valor;
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

// ---------- LED direccionables ----------

// Onda triangular de 0 a 255 con el período indicado (para "respirar" y parpadear suave).
uint8_t onda(unsigned long ahora, unsigned long periodo) {
  unsigned long fase = ahora % periodo;
  unsigned long mitad = periodo / 2;
  return fase < mitad ? fase * 255UL / mitad : (periodo - fase) * 255UL / mitad;
}

uint32_t colorConNivel(Adafruit_NeoPixel &tira, uint8_t r, uint8_t g, uint8_t b, uint8_t nivel) {
  return tira.Color((uint16_t)r * nivel / 255, (uint16_t)g * nivel / 255, (uint16_t)b * nivel / 255);
}

// Solo se envía a los LED si algo cambió (enviar pausa las interrupciones un instante).
void pintar(Adafruit_NeoPixel &tira, uint8_t i, uint32_t c, bool &cambio) {
  if (tira.getPixelColor(i) != c) {
    tira.setPixelColor(i, c);
    cambio = true;
  }
}

void actualizarLeds(unsigned long ahora) {
  if (ahora - ultimoCuadroLed < 20) return;        // 50 cuadros por segundo
  // Mientras el servo se mueve no se tocan los LED: así no tiembla la puerta.
  if (servo.attached() && anguloActual != anguloDestino) return;
  ultimoCuadroLed = ahora;

  bool cambio = false;
  for (uint8_t i = 0; i < LEDS_POR_MODULO; i++) {
    uint32_t c = 0;
    switch (modoLed) {
      case 'N':  // verde que "respira": vigilando
        c = colorConNivel(ledExterior, 0, 255, 70, 6 + (uint16_t)onda(ahora, 4000) * (BRILLO_EXTERIOR - 6) / 255);
        break;
      case 'P':  // ámbar tenue fijo: detección en pausa
        c = colorConNivel(ledExterior, 255, 120, 0, BRILLO_EXTERIOR / 3);
        break;
      case 'A':  // amarillo pulsando rápido: verificando movimiento
        c = colorConNivel(ledExterior, 255, 190, 0, (uint16_t)onda(ahora, 500) * BRILLO_EXTERIOR / 255);
        break;
      case 'R': {  // rojo intenso, mitades alternadas como baliza: sismo
        bool mitad = ((ahora / 120) % 2) == (i < LEDS_POR_MODULO / 2 ? 0 : 1);
        c = mitad ? ledExterior.Color(255, 0, 0) : ledExterior.Color(40, 0, 0);
        break;
      }
      default:
        c = 0;
    }
    pintar(ledExterior, i, c, cambio);
  }
  if (cambio) ledExterior.show();

  // Luz interior: se enciende y apaga de forma suave.
  uint8_t destino = luzInterior ? BRILLO_INTERIOR : 0;
  if (nivelInterior != destino) {
    int siguiente = nivelInterior < destino ? (int)nivelInterior + 10 : (int)nivelInterior - 10;
    nivelInterior = nivelInterior < destino ? (uint8_t)min(siguiente, (int)destino) : (uint8_t)max(siguiente, (int)destino);
    for (uint8_t i = 0; i < LEDS_POR_MODULO; i++) {
      ledInterior.setPixelColor(i, colorConNivel(ledInterior, 255, 200, 140, nivelInterior));
    }
    ledInterior.show();
  }
}
