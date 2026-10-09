# Messenger

Нативный Android-клиент учебного API: список чатов, Paging 3, поиск по загруженным чатам, создание чата, история и отправка сообщений. В portrait отображается один экран, в landscape — список чатов и переписка в соотношении 35/65.

## Сборка

Требуются полный **JDK 21 (с javac)**, Android SDK Platform 35 и Build Tools 35.0.0. Android Studio — версия с поддержкой Android Gradle Plugin 8.9.2 или новее. Gradle Wrapper 8.11.1 и Kotlin 2.1.20 закреплены в проекте. Минимальная версия Android — API 26.

Создайте исключённый из Git файл `local.properties`:

```properties
sdk.dir=/absolute/path/to/android-sdk
messenger.oauthToken=YOUR_TOKEN
```

Вместо `sdk.dir` можно задать `ANDROID_HOME`. Вместо свойства токена можно использовать переменную окружения `MESSENGER_OAUTH_TOKEN`. Значение не выводится в логи. Токен передаётся в APK при сборке: это допустимо для учебного клиента, но не защищает реальные пользовательские секреты. При отсутствии токена приложение собирается, а запросы завершаются сообщением об ошибке конфигурации/подключения.

```bash
./gradlew :app:assembleDebug
./gradlew :core-common:test :core-network:testDebugUnitTest \
  :feature-chats:testDebugUnitTest :feature-messages:testDebugUnitTest
adb install -r app/build/outputs/apk/debug/app-debug.apk
adb shell am start -n ru.messenger.app/.MainActivity
```

В текущей облачной среде сначала выполните:

```bash
source /workspace/toolchains/messenger-env.sh
cd /workspace/android_project1
```

Скрипт `/workspace/toolchains/install-messenger.sh` устанавливает инструменты, использует системный прокси и проверяет сборку/тесты. Он не хранит токен и не перезаписывает `local.properties`.

## Модули и DI

- `app` — Application, AppComponent Dagger 2, Activity и координация контейнеров. Сетевых запросов и бизнес-логики в Activity нет.
- `feature-chats` — публичные `ChatsFeature`, `ChatsDependencies` и фабрика ViewModel в `api`; фрагмент, ViewModel, PagingSource и адаптеры в `internal`.
- `feature-messages` — аналогичные публичные контракты; история, отправка, черновик и прокрутка в `internal`.
- `core-network` — Retrofit, OkHttp, OAuth Interceptor, проверенные DTO, NetworkModule, RepositoryModule и реализация Repository.
- `core-common` — доменные модели, Repository-контракт и общие функции.

Зависимости: `app → features/core`, `features → core-common`, `core-network → core-common`. Feature-модули не зависят друг от друга. Dagger предоставляет сетевые зависимости, источник токена, Repository и обе фабрики ViewModel. Activity создаёт экраны только через публичные feature API.

## Подтверждённый API

Используется проверенный HTTPS: `https://emil-international.ru/`. Cleartext запрещён. Проверки выполнены HTTP-клиентом из облачной среды; Postman в ней не установлен.

- Заголовок: `oauth`, автоматически добавляется OkHttp Interceptor.
- Чат: `id`, `name`.
- Сообщение: `id`, `text`.
- GET чатов с limit/offset: `data`, `total`, `limit`, `offset`.
- GET сообщений: `id`, `messages`, с пагинацией также `total`, `limit`, `offset`.
- POST создания: объект `chats`.
- POST сообщения: объект `id`, `messages`.

Авторов, времени и URL изображений нет. Сообщения сохраняют порядок сервера, имеют нейтральный стиль. Локальный аватар реально загружается Glide с placeholder/error и circleCrop.

Пагинация чатов использует размер 20, смещение по фактическому размеру ответа и total, удаляет повторные ID. ViewModel хранит отдельный снимок загруженных чатов; поиск переключается на обычный ListAdapter и не запрашивает страницы. После создания вызывается refresh. Повторное открытие чата обновляет историю; при повороте данные остаются в ViewModel. Новые чаты находятся в конце списка сервера; чтобы увидеть их, нужно прокрутить до конца либо загрузить их страницу перед поиском.

История читается страницами по 100, чтобы гарантировать полноту. Отправка использует весь список из ответа POST. Автоматические повторы и перенаправления POST отключены, повтор после ошибки выполняет пользователь. Черновики и поиск хранятся в SavedStateHandle. ViewModel сообщений имеет отдельный ключ для каждого чата.

## Облачный эмулятор

В среде установлены AOSP API 26 и API 35 x86_64. Для проверок использован более лёгкий API 26. Без `/dev/kvm` он запускается в программном режиме и загружается медленно. Инструкции запуска сохранены в настройках среды.

Облачный HTTPS-прокси использует собственный доверенный CA. Для проверки на эмуляторе можно собрать **только debug** с дополнительным доверенным сертификатом:

```bash
MESSENGER_CLOUD_CA_FILE=/usr/local/share/ca-certificates/environment-proxy-ca.crt \
  ./gradlew :app:assembleDebug
```

Сертификат копируется в генерируемые ресурсы `build/`, не в исходники. TLS и проверка имени сервера остаются включены. Обычная сборка без этой переменной доверяет только системным CA; release не использует debug-конфигурацию.

При готовом эмуляторе или подключённом устройстве проверка диалога создания запускается командой `./gradlew :app:connectedDebugAndroidTest`. В облаке используйте ту же переменную `MESSENGER_CLOUD_CA_FILE`. Тест открывает диалог, проверяет запрет пустого имени и отменяет его без отправки POST.

Результаты проверок и ограничения см. в [VALIDATION.md](VALIDATION.md).
