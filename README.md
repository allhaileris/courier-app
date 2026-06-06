# Курьер (Courier)

Android-приложение для курьеров с отслеживанием маршрутов и доставками.

## Возможности

- **Карта**: Просмотр локации и маршрутов с помощью OSMdroid
- **Отслеживание**: Фоновый сервис отслеживания геолокации с сохранением треков
- **История треков**: Сохранённые маршруты в локальной базе данных
- **Экспорт треков**: Выгрузка треков в JSON формат
- **Профиль**: Просмотр статистики доставок и рейтинга

## Технологии

- **Kotlin** - основной язык
- **AndroidX** - современные библиотеки Android
- **OSMdroid** - OpenStreetMap карты
- **Room Database** - локальное хранение данных
- **Kotlin Coroutines** - асинхронные операции
- **OkHttp** - сетевые запросы
- **Material Design** - UI компоненты

## Установка

### Требования

- Android 7.0+ (API 24+)
- Android Studio Hedgehog или новее
- JDK 17+

### Сборка

```bash
./gradlew assembleDebug
```

### Установка на устройство

```bash
./gradlew installDebug
```

## Структура проекта

```
app/src/main/java/org/eu/manekineko/courier/
├── data/
│   ├── api/       # API сервисы (DeliveryApi, TrackUpload)
│   ├── dao/       # Доступ к данным (TrackDao)
│   ├── database/  # Room Database
│   └── model/     # Модели данных
├── service/       # Служебные компоненты (TrackingService)
├── ui/            # UI слой
│   ├── map/       # Карта
│   ├── profile/   # Профиль
│   ├── tracks/    # История треков
│   └── trackmap/  # Детальный просмотр трека
└── util/          # Утилиты (FileSaver, TrackExporter)
```

## Разрешения

Приложение использует:
- `ACCESS_FINE_LOCATION` - точная геолокация
- `ACCESS_COARSE_LOCATION` - приблизительная локация
- `FOREGROUND_SERVICE_LOCATION` - фоновое отслеживание
- `INTERNET` - сетевой доступ
- `POST_NOTIFICATIONS` - уведомления

## Лицензия

MIT
