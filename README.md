# GTV Streamer Drive

Lokalny panel NAS dla **Google TV Streamer**. Aplikacja przegląda dysk USB NTFS, otwiera obsługiwane pliki na Android TV i przyjmuje materiały z telefonu przez Wi-Fi — bez chmury i bez trwałego kopiowania plików do pamięci streamera.

> Status: działający prototyp / wersja developerska (`0.1.0`). Zweryfikowany ręcznie na Google TV Streamer z dyskiem USB NTFS przez adapter Ugreen.

## Funkcje

- przeglądanie katalogów i metadanych plików z podłączonego dysku NTFS;
- lokalny serwer HTTP na porcie `8789` do przesyłania z telefonu bezpośrednio na dysk USB;
- usługa pierwszoplanowa, dzięki której serwer działa również po wyjściu z panelu;
- parowanie telefonu kodem QR, ważnym jednorazowo przez 2 minuty;
- trwałe rozpoznawanie sparowanej przeglądarki przez token w ciasteczku;
- awaryjny, sześciocyfrowy kod wysyłania.

## Szybkie użycie

1. Podłącz dysk USB do streamera i uruchom **Panel NAS**.
2. Upewnij się, że karta `ZAPIS` pokazuje `Odczyt i zapis`.
3. Wybierz `Sparuj telefon`, zeskanuj QR aparatem telefonu i otwórz link.
4. Na telefonie wybierz plik na stronie przesyłania.

Telefon i streamer muszą być w tej samej sieci lokalnej. Adres serwera jest widoczny w panelu, przykładowo `http://10.0.0.9:8789`.

## Struktura

| Ścieżka | Rola |
| --- | --- |
| `nas-browser/` | Aplikacja Android TV, panel NAS i serwer telefonu. |
| `third_party/otgmaster/` | Zmodyfikowany backend OTG Master obsługujący USB/NTFS. |
| `docs/NAS_ARCHITECTURE.md` | Przepływ danych i granice uprawnień. |
| `docs/DEVELOPMENT.md` | Środowisko, budowanie i instalacja. |
| `docs/TESTING.md` | Testy wykonane na sprzęcie i regresje. |
| `docs/OPERATIONS.md` | Użytkowanie oraz rozwiązywanie problemów. |
| `docs/SECURITY.md` | Model bezpieczeństwa i ograniczenia sieciowe. |

## Budowanie

Wymagane są Android SDK Platform Tools, SDK 36, Java 17+ oraz Gradle 8.9+.

```powershell
$env:JAVA_HOME = "C:\Program Files\Android\Android Studio\jbr"
gradle -c settings.gtv.gradle.kts :nas-browser:assembleDebug
```

Backend NTFS buduje się niezależnie:

```powershell
Set-Location third_party\otgmaster
.\gradlew.bat :app:assembleDebug
```

Szczegóły, w tym instalacja przez ADB, są w [dokumentacji deweloperskiej](docs/DEVELOPMENT.md).

## Licencje

`third_party/otgmaster` pochodzi z projektu [OTG Master](https://github.com/moylali/OTGMaster) i zawiera jego licencję GPL-3.0. Dystrybucja zmodyfikowanego backendu musi spełniać warunki tej licencji.
