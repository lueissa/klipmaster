# Сборка Android (КлипМастер)

JDK 21 (`/opt/jdk-21`), Android SDK (`/opt/android-sdk`). Capacitor CLI 8 требует Node ≥ 22 — скрипты используют `npx -y node@22`.

```bash
cd /workspace/clipmaster
export JAVA_HOME=/opt/jdk-21 ANDROID_HOME=/opt/android-sdk
npm run cap:sync                                   # копия веба + cap sync
cd android && ./gradlew assembleDebug assembleRelease bundleRelease
```
Выход: `android/app/build/outputs/apk/{debug,release}/`, `android/app/build/outputs/bundle/release/app-release.aab`.

## Подпись релиза
- Ключ: `release/clipmaster-release.jks`, alias `clipmaster`, RSA 4096, срок 10000 дней
- Пароли: `release/keystore.properties` (читается `android/app/build.gradle`; если файла нет — release собирается неподписанным)
- **Потеря ключа = невозможность обновлять приложение в RuStore.** Сохраните `release/` в надёжное место.
- Проверка: `PATH=/opt/jdk-21/bin:$PATH /opt/android-sdk/build-tools/36.0.0/apksigner verify --print-certs dist/KlipMaster-1.0.0-release.apk`
- Новая версия: увеличить `versionCode`/`versionName` в `android/app/build.gradle`.

## Публикация в RuStore
1. Аккаунт разработчика RuStore (console.rustore.ru), заполнить данные.
2. Загрузить `dist/KlipMaster-1.0.0-release.aab` (или APK).
3. Текст и графика — `store/LISTING-RU.md`, `store/icon-512.png`, `store/feature-1024x500.png`; снять скриншоты.
4. Опубликовать `store/privacy.html` по публичному URL, вписать контакт.
5. Отправить на модерацию.
