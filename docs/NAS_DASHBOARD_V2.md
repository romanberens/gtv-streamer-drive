# Panel NAS 0.2.0 beta — ekran główny

**Data przygotowania:** 2026-11-05

## Zmiany

- Nowy ekran Android TV: nagłówek, menu z obsługą pilota, status dysku / wolnego miejsca / zapisu / sparowanych telefonów, przeglądarka katalogów i panel telefonu.
- QR na osobnym widoku z możliwością powrotu do listy plików.
- Ostatnie cztery rzeczywiste zakończone transfery z telefonu; aplikacja zapamiętuje maksymalnie pięć ostatnich nazw/rozmiarów w lokalnych preferencjach. Nie pokazuje przykładowych plików ani fikcyjnych postępów.
- Odświeżanie ekranu po poprawnym uploadzie i parowaniu nawet wtedy, gdy serwer został uruchomiony wcześniej przez usługę.
- Ścieżki SAF są budowane z URI przyznanego drzewa root; odświeżenie nie cofa przeglądarki do katalogu głównego.
- Numer wersji 0.2.0 (versionCode 2).

## Granice tego wydania

- Brak zmian w `third_party/otgmaster` i sposobie montowania dysku.
- Brak wykresów bieżącej prędkości / procentu uploadu; aktualnie wyświetlane są tylko zakończone transfery.
- Wykrywanie plików i prawidłowość otwierania podkatalogów muszą zostać sprawdzone na fizycznym Google TV Streamer.
- Strona telefoniczna nadal korzysta z HTTP w sieci LAN; **nie udostępniać portu 8789 w Internecie**.
- Backend nadal parsuje multipart przed autoryzacją; poprawka bezpieczeństwa jest osobnym zadaniem P0.

## Instalacja testowa (Windows PowerShell)

```powershell
cd "$env:USERPROFILE\Desktop\chromecast"
git fetch origin
git switch -c feature/nas-dashboard-v2 --track origin/feature/nas-dashboard-v2

$env:JAVA_HOME = "C:\Program Files\Android\Android Studio\jbr"
$gradle = "$env:USERPROFILE\.gradle\wrapper\dists\gradle-8.10.2-bin\a04bxjujx95o3nb99gddekhwo\gradle-8.10.2\bin\gradle.bat"
& $gradle -c settings.gtv.gradle.kts :nas-browser:assembleDebug

$adb = "$env:LOCALAPPDATA\Android\Sdk\platform-tools\adb.exe"
& $adb connect "10.0.0.9:5555"
& $adb -s "10.0.0.9:5555" install -r ".\nas-browser\build\outputs\apk\debug\nas-browser-debug.apk"
& $adb -s "10.0.0.9:5555" shell am force-stop pl.local.nasbrowser
& $adb -s "10.0.0.9:5555" shell am start -n pl.local.nasbrowser/.MainActivity
```

Nie używaj `git add .` w mieszanym katalogu roboczym `chromecast`. Nie scalaj PR do `main` przed testem.

## Kryteria odbioru na urządzeniu

1. Ekran ma boczne menu i cztery karty, a nie wcześniejszy układ trzech kart.
2. Pilotem można przejść między menu, plikami, przyciskiem QR oraz powrócić klawiszem Back.
3. Po wejściu w katalog i odświeżeniu pozostajemy w katalogu.
4. Film/zdjęcie otwiera się w odpowiedniej aplikacji.
5. QR paruje telefon, licznik urządzeń odświeża się po sparowaniu.
6. Przesłanie testowego pliku odświeża listę i pojawia się w ostatnich transferach.
7. Status `Tylko odczyt` pojawia się, gdy dysk nie pozwala na zapis.
8. W razie błędu UI sprawdzić logcat, bez usuwania danych aplikacji.

## Weryfikacja

Zmiany zostały poddane statycznej kontroli struktury kodu. Kompilacja w środowisku Android i test na streamerze wymagają wykonania. Na PR działa workflow GitHub Actions `Panel NAS - build debug`.
