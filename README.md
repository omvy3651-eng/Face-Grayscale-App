# Face Grayscale App

एक simple Android app जो front camera से आपका चेहरा पहचानती है:
- अगर सिर्फ **आपका चेहरा** दिखे → पूरी screen **grayscale (black & white)** हो जाती है
- अगर कोई **और चेहरा** दिखे, या कोई चेहरा न दिखे → screen **normal color** में वापस आ जाती है

## सबसे आसान तरीका: GitHub Actions (बिना कुछ install किए APK बनाएं)

Computer पर Android Studio install किए बिना, browser से ही APK बन सकती है:

1. [github.com](https://github.com) पर free account बनाएं (अगर नहीं है)
2. नया repository बनाएं (नाम कुछ भी रखें, जैसे `face-grayscale-app`) — **Public** या **Private** दोनों चल जाएगा
3. इस पूरे `FaceGrayscaleApp` folder को उस repository में upload करें:
   - GitHub की website पर repo खोलें → **"Add file" → "Upload files"** → पूरा folder drag-and-drop करें
   - या अगर `git` जानते हैं: `git init`, `git add .`, `git commit -m "first"`, फिर GitHub पर बताए गए commands से push करें
4. Upload होते ही ऊपर **"Actions"** tab पर जाएं
5. "Build APK" workflow अपने आप चलने लगेगा (2-4 मिनट लगेंगे) — हरा ✅ निशान आने का इंतज़ार करें
6. उस workflow run पर click करें → नीचे **"Artifacts"** section में `app-debug-apk` दिखेगा → उसे download करें
7. यह एक ZIP होगा, उसे extract करने पर असली **`app-debug.apk`** file मिलेगी
8. वह APK file अपने फोन में भेजें (WhatsApp, Google Drive, या USB से) और फोन पर खोलकर install करें
   - फोन पर पहली बार "install from unknown sources" की permission माँगेगा — allow कर दें

बस! इस तरीके में आपके अपने computer पर कुछ भी install करने की ज़रूरत नहीं, सब कुछ GitHub के servers पर होता है।

---



1. [Android Studio](https://developer.android.com/studio) install करें
2. इस पूरे `FaceGrayscaleApp` फ़ोल्डर को Android Studio में **"Open an existing project"** से खोलें
3. Gradle sync होने दें (इंटरनेट चाहिए, पहली बार में थोड़ा समय लगेगा)
4. USB से अपना Android phone connect करें (Developer Options > USB Debugging on रखें)
5. ऊपर **Run ▶** button दबाएं — app आपके फोन में install हो जाएगी

## ज़रूरी one-time setup (बहुत ज़रूरी!)

System-wide grayscale toggle करने के लिए एक special permission चाहिए जो सामान्य popup से नहीं मिलती। App install करने के बाद, phone को computer से जोड़कर एक बार यह ADB command चलाएं:

```bash
adb shell pm grant com.example.facegrayscale android.permission.WRITE_SECURE_SETTINGS
```

(यह वही तरीका है जो Play Store पर मौजूद असली grayscale-toggle apps इस्तेमाल करती हैं, क्योंकि Android इस permission को normal button से देने का कोई तरीका नहीं देता।)

## App इस्तेमाल कैसे करें

1. App खोलें, camera permission दें
2. सीधे कैमरा में देखें और **"Enroll My Face"** दबाएं (एक बार करना है)
3. **"Start Watching"** दबाएं — अब app background में front camera से आपका चेहरा check करती रहेगी
4. जब भी आप फोन देखेंगे अकेले → screen grayscale हो जाएगी
5. जब कोई और देखे या कोई face न मिले → color वापस आ जाएगा
6. बंद करने के लिए **"Stop Watching"** दबाएं

## सीमाएं (Limitations) — ईमानदारी से बताना ज़रूरी है

- यह **face recognition** नहीं बल्कि एक **simple geometric check** (eye-to-face ratio) है। यह security के लिए नहीं है, सिर्फ digital-wellbeing के लिए एक approximate तरीका है। कभी-कभी galat pehchaan भी हो सकती है
- Front camera लगातार चलने से **battery जल्दी खर्च** होगी
- कुछ नए Android versions (14+) पर background camera access पर extra restrictions हो सकते हैं
- यह app Play Store पर publish करने लायक production-quality नहीं है — यह एक personal/experimental use के लिए starting point है

## Files की जानकारी

- `MainActivity.java` — permission, face enroll UI
- `FaceWatchService.java` — background में चलने वाली camera + face detection service
- `GrayscaleController.java` — screen grayscale on/off करने का logic
