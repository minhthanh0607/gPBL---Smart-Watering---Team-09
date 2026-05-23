# Smart Watering System (Hệ Thống Tưới Nước Thông Minh) - Team 9

Một hệ thống IoT giám sát độ ẩm, nhiệt độ và tự động tưới nước thông minh cho cây trồng, kết hợp điều khiển đa phương thức: Tự động theo cảm biến, hẹn giờ tuần hoàn, nút bấm cảm ứng tại chỗ và điều khiển từ xa qua giao diện Web/Mobile.

---

## 📌 Các Tính Năng Chính (Key Features)

* **Tự động hóa theo độ ẩm (Smart Automation):** Hệ thống tự động kích hoạt máy bơm khi độ ẩm không khí giảm xuống dưới 50% và tự ngắt khi đạt trên 75%.
* **Kiểm tra tuần hoàn (Auto Test Mode):** Cứ mỗi 10 giây (cấu hình thử nghiệm), hệ thống tự động bật bơm trong 3 giây để kiểm tra trạng thái vận hành của toàn mạch.
* **Điều khiển thủ công tại chỗ (Manual Touch Control):** Sử dụng cảm biến chạm (Touch Sensor) để bật/tắt bơm ngay lập tức (Chế độ Override ngắt tạm thời các logic tự động).
* **Cảnh báo an toàn (Water Level & Buzzer Alert):** Khi mực nước trong bồn chứa xuống thấp hơn ngưỡng an toàn, còi (Buzzer) sẽ kêu ngắt quãng liên tục để cảnh báo cạn nước.
* **Giám sát & Điều khiển từ xa (Web/App Integration):** Tích hợp HTTP Web Server trên bo mạch, cung cấp dữ liệu qua API dạng JSON cho ứng dụng Web/Mobile điều khiển tưới từ xa.

---

## 🛠️ Thành Phần Hệ Thống (System Components)

### 1. Phần Cứng (Hardware)
* **MCU:** Arduino UNO R4 WiFi (hoặc board mạch hỗ trợ thư viện `WiFiS3`)
* **Sensors:** * Cảm biến nhiệt độ & độ ẩm DHT11
  * Cảm biến mực nước (Water Level Sensor)
  * Cảm biến chạm điện dung (Touch Sensor)
* **Actuators:**
  * Relay kích dòng + Máy bơm nước mini (Kích mức THẤP - Active LOW)
  * Còi báo động (Buzzer)

### 2. Sơ Đồ Chân Kết Nối (Pin Configuration)

| Linh Kiện (Component) | Chân Arduino (Pin) | Chế Độ (Mode) | Ghi Chú (Notes) |
| :--- | :--- | :--- | :--- |
| **DHT11 Sensor** | `Pin 5` | `INPUT` | Đọc nhiệt độ/độ ẩm không khí |
| **Relay / Pump** | `Pin 7` | `OUTPUT` | Active LOW (Mức 0 là BẬT bơm) |
| **Buzzer** | `Pin 12` | `OUTPUT` | Cảnh báo khi hết nước |
| **Touch Sensor** | `Pin 2` | `INPUT` | Nút bấm cảm ứng điều khiển tại chỗ |
| **Water Level Sensor** | `Pin A0` | `ANALOG INPUT` | Giám sát mực nước bồn chứa |

---

## 🌐 API Endpoint (JSON Response)

Khi Web App gửi yêu cầu `GET` tới địa chỉ IP của Arduino, bo mạch sẽ phản hồi một chuỗi dữ liệu JSON với cấu trúc như sau để hiển thị lên UI:

```json
{
  "temp": 28.5,
  "humidity": 65.0,
  "watered": false,
  "seconds_since_last_run": 45,
  "manual_mode": false
}
---

## 👥 Credits & Contributions

This project was developed by **Team 9** with roles defined as follows:

* **Nguyễn Minh Thành (Ken):** Responsible for hardware design, circuit assembly, and embedding the core control logic (sensor data processing, multitasking with `millis()`, and actuator safety controls).
* **[Điền họ tên bạn mi vô đây]:** Responsible for designing and developing the Frontend Web UI. Additionally, they co-wrote the HTTP Web Server and JSON API handling within the Arduino firmware to ensure seamless integration between the hardware and the web application.
