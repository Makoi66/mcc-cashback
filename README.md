# MCC Кешбэк

Android-приложение: вводишь MCC (4 цифры) — видишь, в какую категорию кешбэка он попадает в каждом банке.

```
data/       JSON, который приложение скачивает по Sync (формат — data/README.md)
sources/    исходные PDF банков и справочник MCC
pipeline/   Python: проверка JSON против PDF (check) и сборка index.json (publish)
android/    приложение (Kotlin + Compose)
```

Обновление данных: PDF банка → JSON в `data/banks/` (переносится вручную, `check` ловит опечатки) → push на GitHub → Sync в приложении.
Снимок `data/` вшивается в APK при сборке, так что приложение работает и без Sync.

## Сборка

```sh
cd android
JAVA_HOME=/opt/android-studio/jbr ./gradlew testDebugUnitTest assembleDebug
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

Адрес, откуда Sync берёт данные, задан в `android/app/build.gradle.kts` (`defaultDataUrl`)
и меняется в приложении: «Банки» → «Адрес данных».

## Pipeline

```sh
cd pipeline     # нужен только python3 и pdftotext (poppler)
python3 -m mcc_tools.check ../data/banks/vtb.json ../sources/vtb/*.pdf   # коды из JSON есть в PDF?
python3 -m mcc_tools.publish     # проверить data/banks/*.json, пересобрать index.json и словарь
```

Справочник MCC — [Oleksios/Merchant-Category-Codes](https://github.com/Oleksios/Merchant-Category-Codes) (MIT).
