#include "WiFiS3.h"
#include "DHT.h"

// --- Connection Settings ---
const char* ssid = "Yêu mỗi mình em";
const char* password = "22222222";

// --- Pin Configuration ---
#define DHTPIN 5
#define DHTTYPE DHT11
#define PUMP_PIN 7         // Relay/Pump (Active LOW)
#define WATER_LEVEL_PIN A0 // Water level sensor
#define BUZZER_PIN 12      // Buzzer
#define TouchSensor 2      // Touch sensor

DHT dht(DHTPIN, DHTTYPE);
WiFiServer server(80);

// --- State Variables ---
int PUMP_STATE = 0;
int lastInputState = 1;

// --- Timers ---
unsigned long lastDHTTime = 0;
unsigned long lastWifiTime = 0;
unsigned long lastBuzzerTime = 0;
unsigned long appPumpStartTime = 0;
unsigned long autoTestTimer = 0;
unsigned long lastPumpStartTime = 0;

// --- Flags ---
bool isBuzzerOn = false;
bool isAppPumping = false;
bool isAutoTestRunning = false;
bool isManualOverride = false;

// --- Thresholds ---
const int WATER_LOW_THRESHOLD = 300;
const unsigned long AUTO_INTERVAL = 10000; // 10s wait
const unsigned long PUMP_DURATION = 3000;  // 3s run

void setup() {
  Serial.begin(115200);

  pinMode(PUMP_PIN, OUTPUT);
  pinMode(BUZZER_PIN, OUTPUT);
  pinMode(TouchSensor, INPUT);

  digitalWrite(PUMP_PIN, HIGH);
  digitalWrite(BUZZER_PIN, HIGH);

  dht.begin();

  Serial.print("Connecting to WiFi...");
  WiFi.begin(ssid, password);

  while (WiFi.status() != WL_CONNECTED) {
    delay(500);
    Serial.print(".");
  }

  Serial.println("\nWiFi Connected!");
  delay(2000); // Delay ở setup thì không sao
  Serial.print("IP Address: ");
  Serial.println(WiFi.localIP());

  server.begin();
  lastInputState = digitalRead(TouchSensor);
  autoTestTimer = millis(); // Start counting from now
}

void loop() {
  unsigned long currentMillis = millis();

  checkWaterLevel(currentMillis);
  handleTouchSensor(currentMillis); // Pass currentMillis to sync timers

  if (!isManualOverride) {
    runAutoTest(currentMillis);
    handleAppPump(currentMillis);

    if (currentMillis - lastDHTTime >= 2000) {
      automaticWatering();
      lastDHTTime = currentMillis;
    }
  }

  if (currentMillis - lastWifiTime >= 50) {
    handleWiFi();
    lastWifiTime = currentMillis;
  }
}

void checkWaterLevel(unsigned long currentMillis) {
  int level = analogRead(WATER_LEVEL_PIN);
  if (level < WATER_LOW_THRESHOLD) {
    if (!isBuzzerOn && (currentMillis - lastBuzzerTime >= 500)) {
      digitalWrite(BUZZER_PIN, LOW);
      isBuzzerOn = true;
      lastBuzzerTime = currentMillis;
    }
    else if (isBuzzerOn && (currentMillis - lastBuzzerTime >= 100)) {
      digitalWrite(BUZZER_PIN, HIGH);
      isBuzzerOn = false;
      lastBuzzerTime = currentMillis;
    }
  } else {
    digitalWrite(BUZZER_PIN, HIGH);
    isBuzzerOn = false;
  }
}

void runAutoTest(unsigned long currentMillis) {
  // Wait for 10 seconds
  if (!isAutoTestRunning && (currentMillis - autoTestTimer >= AUTO_INTERVAL)) {
    startPump("Auto Test Start");
    isAutoTestRunning = true;
    autoTestTimer = currentMillis;
  }

  // Run for 3 seconds
  if (isAutoTestRunning && (currentMillis - autoTestTimer >= PUMP_DURATION)) {
    stopPump("Auto Test End");
    isAutoTestRunning = false;
    autoTestTimer = currentMillis; // Start 10s countdown again from here
  }
}

void handleTouchSensor(unsigned long currentMillis) {
  int inputState = digitalRead(TouchSensor);
  if (lastInputState == 0 && inputState == 1) {
    if (PUMP_STATE == 0) {
      isManualOverride = true;
      startPump("Manual ON");
    } else {
      isManualOverride = false;
      isAutoTestRunning = false; // Reset auto-run state
      stopPump("Manual OFF");

      // CRITICAL: Reset the timer to CURRENT time so the 10s countdown starts NOW
      autoTestTimer = currentMillis;
      Serial.println("Auto-timer reset to 0. Waiting 10s...");
    }
    delay(50);
  }
  lastInputState = inputState;
}

void handleAppPump(unsigned long currentMillis) {
  if (isAppPumping && (currentMillis - appPumpStartTime >= PUMP_DURATION)) {
    stopPump("App Timer Finished");
    isAppPumping = false;
    autoTestTimer = currentMillis; // Also reset auto-timer after app watering
  }
}

void automaticWatering() {
  float h = dht.readHumidity();
  if (isnan(h)) return;
  if (h < 50.0 && PUMP_STATE == 0) {
    startPump("Low Humidity");
  } else if (h > 75.0 && PUMP_STATE == 1 && !isManualOverride) {
    stopPump("Humidity Target Reached");
  }
}

void startPump(String reason) {
  digitalWrite(BUZZER_PIN, LOW);
  delay(100);
  digitalWrite(BUZZER_PIN, HIGH);
  digitalWrite(PUMP_PIN, LOW);
  PUMP_STATE = 1;
  lastPumpStartTime = millis();
  Serial.println(">>> Pump ON | Reason: " + reason);
}

void stopPump(String reason) {
  digitalWrite(PUMP_PIN, HIGH);
  PUMP_STATE = 0;
  Serial.println("<<< Pump OFF | Reason: " + reason);
}

void handleWiFi() {
  WiFiClient client = server.available();
  if (client) {
    String request = "";
    while (client.connected()) {
      if (client.available()) {
        char c = client.read();
        request += c;
        if (c == '\n') {
          if (request.indexOf("GET /water") != -1) {
            startPump("Web Command");
            isAppPumping = true;
            appPumpStartTime = millis();
            sendJsonResponse(client, true);
          } else if (request.indexOf("GET / ") != -1) {
            sendJsonResponse(client, false);
          }
          break;
        }
      }
    }
    client.stop();
  }
}

void sendJsonResponse(WiFiClient& client, bool isWatered) {
  float h = dht.readHumidity();
  float t = dht.readTemperature();
  unsigned long secondsSinceLastRun = (millis() - lastPumpStartTime) / 1000;

  client.println("HTTP/1.1 200 OK");
  client.println("Content-Type: application/json");
  client.println("Access-Control-Allow-Origin: *");
  client.println("Connection: close");
  client.println();

  client.print("{\"temp\":"); client.print(isnan(t) ? 0 : t);
  client.print(",\"humidity\":"); client.print(isnan(h) ? 0 : h);
  client.print(",\"watered\":"); client.print(isWatered ? "true" : "false");
  client.print(",\"seconds_since_last_run\":"); client.print(secondsSinceLastRun);
  client.print(",\"manual_mode\":"); client.print(isManualOverride ? "true" : "false");
  client.println("}");
}
