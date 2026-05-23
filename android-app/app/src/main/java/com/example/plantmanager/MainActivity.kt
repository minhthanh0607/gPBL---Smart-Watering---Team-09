package com.example.plantmanager

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.plantmanager.ui.theme.PlantManagerTheme
import java.time.LocalDateTime
import java.time.Duration
import kotlinx.coroutines.delay
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject

// --- 通信設定 ---
const val ARDUINO_IP = "http://172.20.10.13" // Arduinoに表示されたIPを入力

enum class Language { JP, VN, EN }

data class UIStrings(
    val targetCondition: String, val interval: String, val amount: String,
    val humidity: String, val waterNow: String, val emergencyWater: String,
    val timeSinceLast: String, val sensorSim: String, val auto: String,
    val manual: String, val danger: String
)

val translations = mapOf(
    Language.JP to UIStrings("この花の理想の条件", "水やりの間隔", "水量", "湿度", "水をあげる", "緊急で水をあげる", "前回の水やりからの経過時間", "リアルタイムセンサー値", "自動", "手動", "警告：水が足りません！"),
    Language.VN to UIStrings("Điều kiện lý tưởng", "Khoảng cách", "Lượng nước", "Độ ẩm", "Tưới nước", "Tưới khẩn cấp", "Kể từ lần tưới cuối", "Giá trị cảm biến", "TỰ ĐỘNG", "THỦ CÔNG", "NGUY HIỂM: Cây sắp héo!"),
    Language.EN to UIStrings("Ideal Conditions", "Interval", "Amount", "Humidity", "Water Now", "Emergency Water", "Time since last watering", "Real-time Sensor", "AUTO", "MANUAL", "DANGER: Plant is wilting!")
)

data class PlantProfile(
    val names: Map<Language, String>, val icon: String, val mainColor: Color,
    val bgColor: Color, val idealHumidity: Float, val idealIntervalHours: Int, val waterAmountMl: Int
)

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            PlantManagerTheme {
                Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
                    UltimatePlantUI()
                }
            }
        }
    }
}

@Composable
fun UltimatePlantUI() {
    val client = remember { OkHttpClient() }
    val scope = rememberCoroutineScope()

    val plants = remember {
        listOf(
            PlantProfile(mapOf(Language.JP to "チューリップ", Language.VN to "Tulip", Language.EN to "Tulip"), "🌷", Color(0xFFF06292), Color(0xFFFCE4EC), 0.50f, 24, 150),
            PlantProfile(mapOf(Language.JP to "バラ", Language.VN to "Hoa hồng", Language.EN to "Rose"), "🌹", Color(0xFFE53935), Color(0xFFFFEBEE), 0.40f, 48, 300),
            PlantProfile(mapOf(Language.JP to "ひまわり", Language.VN to "Hướng dương", Language.EN to "Sunflower"), "🌻", Color(0xFFFFB300), Color(0xFFFFF8E1), 0.60f, 12, 500),
            PlantProfile(mapOf(Language.JP to "マーガレット", Language.VN to "Hoa cúc", Language.EN to "Daisy"), "🌼", Color(0xFF9E9E9E), Color(0xFFF5F5F5), 0.45f, 36, 100)
        )
    }

    var selectedPlant by remember { mutableStateOf(plants[0]) }
    var currentLang by remember { mutableStateOf(Language.JP) }
    var lastWateredTime by remember { mutableStateOf<LocalDateTime?>(null) }
    var currentTime by remember { mutableStateOf(LocalDateTime.now()) }
    var currentHumidity by remember { mutableStateOf(0.6f) }
    var currentTemp by remember { mutableStateOf(25.0f) }

    // ポンプ作動状態と手動/自動モードの同期用
    var isAppPumping by remember { mutableStateOf(false) }
    var isManualModeByArduino by remember { mutableStateOf(false) }

    val ui = translations[currentLang]!!

    // Arduinoからデータを取得（変更点：manual_modeの取得を追加）
    suspend fun fetchSensorData() {
        withContext(Dispatchers.IO) {
            try {
                val request = Request.Builder().url(ARDUINO_IP).build()
                client.newCall(request).execute().use { response ->
                    if (response.isSuccessful) {
                        val json = JSONObject(response.body?.string() ?: "{}")
                        currentTemp = json.optDouble("temp", 25.0).toFloat()
                        val rawHumidity = json.optDouble("humidity", 60.0).toFloat()
                        currentHumidity = rawHumidity / 100f

                        // 手動オーバーライド状態の同期
                        isManualModeByArduino = json.optBoolean("manual_mode", false)

                        // 経過秒数から正確な水やり時間を計算
                        val secondsSinceRun = json.optLong("seconds_since_last_run", -1)
                        if (secondsSinceRun >= 0) {
                            lastWateredTime = LocalDateTime.now().minusSeconds(secondsSinceRun)

                            // ポンプが止まっていたら（3秒以上経過）、アプリのSTOP表示を戻す
                            if (secondsSinceRun > 3) {
                                isAppPumping = false
                            }
                        }
                    }
                }
            } catch (e: Exception) { e.printStackTrace() }
        }
    }

    fun sendWaterRequest() {
        isAppPumping = true
        lastWateredTime = LocalDateTime.now()
        scope.launch(Dispatchers.IO) {
            try {
                val request = Request.Builder().url("$ARDUINO_IP/water").build()
                client.newCall(request).execute()
            } catch (e: Exception) { e.printStackTrace() }
        }
    }

    fun sendStopRequest() {
        isAppPumping = false
        scope.launch(Dispatchers.IO) {
            try {
                // Arduino側に/stopエンドポイントがある場合
                val request = Request.Builder().url("$ARDUINO_IP/stop").build()
                client.newCall(request).execute()
            } catch (e: Exception) { e.printStackTrace() }
        }
    }

    LaunchedEffect(Unit) {
        while(true) {
            fetchSensorData()
            currentTime = LocalDateTime.now()
            delay(5000)
        }
    }

    val isDanger = currentHumidity < (selectedPlant.idealHumidity / 2)
    val themeColor = if (isDanger) Color(0xFFE57373) else selectedPlant.mainColor
    val animatedBg by animateColorAsState(targetValue = if (isDanger) Color(0xFFFFEBEE) else selectedPlant.bgColor)

    val timeSinceText = lastWateredTime?.let {
        val diff = Duration.between(it, currentTime)
        String.format("%02d:%02d:%02d", diff.toHours(), diff.toMinutes() % 60, diff.seconds % 60)
    } ?: "--:--:--"

    Box(modifier = Modifier.fillMaxSize().background(animatedBg)) {
        Column(modifier = Modifier.fillMaxSize().padding(horizontal = 24.dp, vertical = 40.dp).verticalScroll(rememberScrollState())) {

            // 言語・スイッチ（Arduinoのmanual_modeと連動）
            Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Language.entries.forEach { lang ->
                    TextButton(onClick = { currentLang = lang }) {
                        Text(lang.name, color = if(currentLang == lang) themeColor else Color.Gray, fontSize = 12.sp)
                    }
                }
                Spacer(modifier = Modifier.weight(1f))
                Text(
                    text = if(isManualModeByArduino) ui.manual else ui.auto,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold
                )
                // スイッチはArduinoからの状態を表示
                Switch(checked = !isManualModeByArduino, onCheckedChange = { }, modifier = Modifier.scale(0.7f), enabled = false)
            }

            // 植物タブ
            ScrollableTabRow(selectedTabIndex = plants.indexOf(selectedPlant), containerColor = Color.Transparent, divider = {}, indicator = {}) {
                plants.forEach { plant ->
                    Tab(selected = selectedPlant == plant, onClick = { selectedPlant = plant }) {
                        Text(plant.names[currentLang]!!, modifier = Modifier.padding(8.dp), color = if(selectedPlant == plant) themeColor else Color.Gray)
                    }
                }
            }

            Spacer(modifier = Modifier.height(20.dp))

            // メインメーター
            Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Box(contentAlignment = Alignment.Center, modifier = Modifier.size(140.dp)) {
                    CircularProgressIndicator(
                        progress = { currentHumidity },
                        modifier = Modifier.fillMaxSize(),
                        strokeWidth = 8.dp,
                        color = if(isDanger) Color.Red else themeColor,
                        trackColor = Color.LightGray.copy(alpha = 0.3f)
                    )
                    Text(selectedPlant.icon, fontSize = 60.sp)
                }

                Column(modifier = Modifier.padding(start = 20.dp)) {
                    StatusRow(Icons.Default.Thermostat, "${currentTemp}°C", iconColor = themeColor, textColor = Color.Black)
                    StatusRow(Icons.Default.WaterDrop, "${(currentHumidity * 100).toInt()}%", iconColor = themeColor, textColor = Color.Black)
                    StatusRow(Icons.Default.Timer, timeSinceText, iconColor = themeColor, textColor = Color.Black)
                    Text(ui.timeSinceLast, fontSize = 10.sp, color = Color.Gray)
                }
            }

            if (isDanger) {
                Text(ui.danger, color = Color.Red, fontSize = 14.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 8.dp))
            }

            Spacer(modifier = Modifier.height(24.dp))

            // 詳細条件カード
            Surface(color = Color.White.copy(alpha = 0.6f), shape = RoundedCornerShape(16.dp)) {
                Column(modifier = Modifier.padding(16.dp).fillMaxWidth()) {
                    Text(ui.targetCondition, fontWeight = FontWeight.Bold, fontSize = 14.sp, color = themeColor)
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        InfoBit(ui.interval, "${selectedPlant.idealIntervalHours}h")
                        InfoBit(ui.amount, "${selectedPlant.waterAmountMl}ml")
                        InfoBit(ui.humidity, "${(selectedPlant.idealHumidity*100).toInt()}%")
                    }
                }
            }

            Spacer(modifier = Modifier.height(40.dp))
            Text("接続中: $ARDUINO_IP", fontSize = 11.sp, color = Color.Gray)

            Spacer(modifier = Modifier.weight(1f))

            // 水やりボタン（STOP切り替え対応）
            Button(
                onClick = { if (isAppPumping) sendStopRequest() else sendWaterRequest() },
                modifier = Modifier.fillMaxWidth().height(60.dp).shadow(8.dp, CircleShape),
                shape = CircleShape,
                colors = ButtonDefaults.buttonColors(
                    containerColor = if(isAppPumping) Color.Red else (if(isDanger) Color.Red else themeColor)
                )
            ) {
                Icon(imageVector = if (isAppPumping) Icons.Default.Stop else Icons.Default.WaterDrop, contentDescription = null)
                Spacer(Modifier.width(10.dp))
                Text(
                    text = when {
                        isAppPumping -> "STOP"
                        isDanger -> ui.emergencyWater
                        else -> ui.waterNow
                    },
                    fontWeight = FontWeight.Bold
                )
            }
            Spacer(modifier = Modifier.height(20.dp))
        }
    }
}

@Composable
fun StatusRow(icon: ImageVector, text: String, iconColor: Color, textColor: Color) {
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(vertical = 4.dp)) {
        Icon(icon, contentDescription = null, tint = iconColor, modifier = Modifier.size(24.dp))
        Spacer(Modifier.width(12.dp))
        Text(text, fontWeight = FontWeight.Bold, fontSize = 18.sp, color = textColor)
    }
}

@Composable
fun InfoBit(label: String, value: String) {
    Column(modifier = Modifier.padding(top = 8.dp)) {
        Text(label, fontSize = 10.sp, color = Color.Gray)
        Text(value, fontWeight = FontWeight.Bold, fontSize = 16.sp, color = Color.Black)
    }
}