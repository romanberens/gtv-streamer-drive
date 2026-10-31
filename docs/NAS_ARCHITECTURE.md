# Architektura NAS

```text
Telefon (przeglądarka)
        | HTTP / Wi-Fi :8789
        v
nas-browser / PhoneUploadServer
        | Android DocumentsContract
        v
OTG Master DocumentsProvider <--- TvBridgeProvider
        | libaums + NTFS
        v
Dysk USB NTFS
```

`nas-browser` (`pl.local.nasbrowser`) jest aplikacją Leanback. Pokazuje stan dysku, wolne miejsce, tryb zapisu, telefony i listę plików. Katalogi są odczytywane przez `DocumentsContract`, a nie przez bezpośrednie ścieżki systemowe.

`UploadServerService` to usługa pierwszoplanowa `dataSync`, która utrzymuje NanoHTTPD na porcie `8789` po wyjściu użytkownika z panelu.

Backend `third_party/otgmaster` montuje dysk i publikuje go przez `app.fayaz.otgmaster.documents`. Dodany `TvBridgeProvider` (`app.fayaz.otgmaster.tvbridge`) jest mostem chronionym uprawnieniem podpisowym. Ujawnia tylko metadane dysku i przyznaje aplikacji NAS dostęp URI do drzewa dokumentów; nie udostępnia urządzeń blokowych ani poświadczeń.

## Transfer

1. Telefon wysyła formularz `multipart/form-data`.
2. Serwer sprawdza parowanie albo kod awaryjny, stan dysku i wolne miejsce.
3. `DocumentsContract.createDocument` tworzy plik na docelowym dysku.
4. Dane są kopiowane bezpośrednio do providera.
5. Częściowy dokument jest usuwany po błędzie.

## Parowanie

QR zawiera losowy token 24-bajtowy, ważny przez 120 sekund i jednorazowy. Po użyciu serwer tworzy osobny token urządzenia o długości 32 bajtów, zapisuje wyłącznie jego SHA-256 i przekazuje przeglądarce w ciasteczku `HttpOnly`, `SameSite=Strict`, ważnym rok.
