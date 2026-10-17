/*
  EHRIA - Estación comunitaria (Arduino Uno)

  Lee temperatura y humedad (DHT), la distancia a una persona (ultrasónico HC-SR04),
  mueve el servo de la puerta del botiquín y controla dos LED (exterior e interior).
  Se comunica con el teléfono por el cable USB a 115200 baudios. El protocolo está descrito
  en app/src/main/java/com/example/detectormovimiento/ProtocoloArduino.kt

  Librerías (Arduino IDE > Herramientas > Administrar bibliotecas):
    - "DHT sensor library" de Adafruit (instala también "Adafruit Unified Sensor")
    - "Servo" (viene incluida)

  Conexiones:
    DHT11/DHT22   DATA -> D2      VCC -> 5V   GND -> GND  (resistencia de 10k entre DATA y 5V
                                                            si el módulo no la trae)
    HC-SR04       TRIG -> D9      ECHO -> D10 VCC -> 5V   GND -> GND
    Servo         señal -> D3     rojo -> 5V EXTERNO (no del Arduino)  marrón -> GND común
    LED exterior  D5 -> resistencia 220 ohm -> LED -> GND
    LED interior  D6 -> resistencia 220 ohm -> LED -> GND
*/

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

const int ANGULO_CERRADO = 0;    // ajustar según cómo quede montado el servo
const int ANGULO_ABIERTO = 95;
const unsigned long CADA_LECTURA_MS = 250;   // envío de datos al teléfono
const unsigned long CADA_DHT_MS = 2000;      // el DHT no se puede leer más rápido
const char *VERSION = "1";

DHT dht(PIN_DHT, TIPO_DHT);
Servo servo;

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

String linea = "";

void setup() {
  Serial.begin(115200);
  pinMode(PIN_TRIG, OUTPUT);
  pinMode(PIN_ECHO, INPUT);
  pinMode(PIN_LED_EXTERIOR, OUTPUT);
  pinMode(PIN_LED_INTERIOR, OUTPUT);
  digitalWrite(PIN_LED_INTERIOR, LOW);
  dht.begin();
  servo.attach(PIN_SERVO);
  servo.write(ANGULO_CERRADO);
  saludar();
}

void loop() {
  unsigned long ahora = millis();

  leerComandos();
  moverServo(ahora);
  actualizarLedExterior(ahora);

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
      break;
    case 'L':  // luz interior
      digitalWrite(PIN_LED_INTERIOR, valor == '1' ? HIGH : LOW);
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
    return;
  }
  if (ahora - ultimoPasoServo < 12) return;   // ~1 s de recorrido completo
  ultimoPasoServo = ahora;
  anguloActual += (anguloDestino > anguloActual) ? 1 : -1;
  servo.write(anguloActual);
}

void actualizarLedExterior(unsigned long ahora) {
  bool encendido;
  switch (modoLed) {
    case 'N': encendido = (ahora % 3000UL) < 120; break;    // destello cada 3 s: vigilando
    case 'P': encendido = (ahora % 2000UL) < 1000; break;   // lento: en pausa
    case 'A': encendido = (ahora % 500UL) < 250; break;     // rápido: evaluando
    case 'R': encendido = (ahora % 200UL) < 100; break;     // muy rápido: sismo
    default: encendido = false;
  }
  digitalWrite(PIN_LED_EXTERIOR, encendido ? HIGH : LOW);
}
