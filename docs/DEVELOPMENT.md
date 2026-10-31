# Środowisko deweloperskie i wdrażanie

## Środowisko użyte podczas prac

| Element | Wartość |
| --- | --- |
| Host | Windows, PowerShell |
| IDE/JDK | Android Studio JBR, Java 17+; użyto JDK 21 |
| Android SDK | `compileSdk 36` |
| NAS Browser | AGP 8.7.3, Kotlin 2.0.21, Compose BOM 2024.12.01 |
| Gradle dla NAS Browser | 8.9 lub nowszy; weryfikowano na 8.10.2 |
| OTG Master | własny wrapper Gradle 8.7, AGP 8.5.2 |
| NDK backendu | 26.1.10909125 |
| Urządzenie | Google TV Streamer, ADB po Wi-Fi |
| ABI urządzenia | `armeabi-v7a, armeabi` |

Backend jest celowo budowany dla `armeabi-v7a`. Przed zmianą filtrów ABI sprawdź `adb shell getprop ro.product.cpu.abilist`.

## Wymagania

- Android SDK z `platform-tools`, API 36 i NDK 26.1.10909125;
- Java 17 lub nowsza;
- odpowiednio zasilany adapter/hub USB do testów z dyskiem;
- wystarczająco dużo miejsca na dysku systemowym — Gradle i NDK tworzą duże cache.

## Budowanie

### Panel NAS

Użyj `settings.gtv.gradle.kts`, aby ominąć niepowiązane moduły z historycznego katalogu roboczego:

```powershell
$env:JAVA_HOME = "C:\Program Files\Android\Android Studio\jbr"
gradle -c settings.gtv.gradle.kts :nas-browser:assembleDebug
```

APK: `nas-browser/build/outputs/apk/debug/nas-browser-debug.apk`.

### Backend NTFS

```powershell
Push-Location third_party\otgmaster
.\gradlew.bat :app:assembleDebug
Pop-Location
```

APK: `third_party/otgmaster/app/build/outputs/apk/debug/app-debug.apk`.

Oba APK muszą być podpisane tym samym kluczem debugowym, ponieważ most do backendu jest chroniony uprawnieniem `signature`.

## Instalacja przez ADB

```powershell
$adb = "$env:LOCALAPPDATA\Android\Sdk\platform-tools\adb.exe"
& $adb connect STREAMER_IP:5555
& $adb install -r third_party\otgmaster\app\build\outputs\apk\debug\app-debug.apk
& $adb install -r nas-browser\build\outputs\apk\debug\nas-browser-debug.apk
& $adb shell am start -n pl.local.nasbrowser/.MainActivity
```

Instaluj backend przed panelem NAS. Przy pierwszym podłączeniu USB uruchom backend i zaakceptuj systemowy dostęp do urządzenia, jeśli system go zażąda.

## Diagnostyka runtime

```powershell
& $adb shell dumpsys activity services pl.local.nasbrowser
& $adb shell "ss -ltn 2>/dev/null | grep 8789"
& $adb shell dumpsys usb
& $adb shell "ls -l /dev/block/sd* 2>/dev/null"
```
