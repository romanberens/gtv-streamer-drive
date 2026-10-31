# Obsługa i rozwiązywanie problemów

## Codzienne użycie

- Otwórz `Panel NAS` na Google TV Streamer.
- Sprawdź, czy `DYSK USB` pokazuje `NTFS`, a `ZAPIS` pokazuje `Odczyt i zapis`.
- Sparuj telefon QR tylko raz.
- Przesyłaj pliki ze strony pod adresem pokazanym na telewizorze.
- Nie odłączaj dysku ani adaptera podczas transferu.

## „Serwer odrzucił połączenie”

1. Otwórz panel NAS przynajmniej raz po restarcie streamera.
2. Sprawdź adres IP i czy telefon jest w tej samej sieci Wi-Fi.
3. Sprawdź VPN, izolację klientów Wi-Fi oraz sieć dla gości.
4. W razie potrzeby uruchom panel ponownie. Usługa pierwszoplanowa powinna utrzymywać serwer po opuszczeniu aplikacji.

## Dysk jest tylko do odczytu

NTFS może zostać zamontowany tylko do odczytu po niepoprawnym odłączeniu albo hibernacji Windows. Podłącz dysk do Windows i uruchom, zastępując `X:` jego literą:

```text
chkdsk X: /f
```

Następnie bezpiecznie wysuń dysk, podłącz go ponownie do streamera i odśwież panel. Nie wymuszaj zapisu na uszkodzonym systemie plików.

## Dysk nie jest wykrywany

- odłącz i podłącz ponownie cały adapter/hub, nie tylko dysk;
- sprawdź zasilanie huba oraz przewód;
- sprawdź, czy `dumpsys usb` widzi urządzenie;
- przy dyskach 3,5 cala użyj własnego zasilania dysku/huba;
- uruchom backend OTG Master i zaakceptuj uprawnienie USB.

## Usuwanie parowania

W obecnej wersji tokeny są przechowywane w danych aplikacji Android. Pełne wyczyszczenie parowań można wykonać przez ustawienia Androida: dane aplikacji `Panel NAS` → `Wyczyść dane`. Usuwa to także lokalny kod awaryjny. Panel zarządzania pojedynczymi urządzeniami jest planowany.
