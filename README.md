# Ola Verde

App Android que aprende los ciclos de los semáforos con el GPS de tus viajes y propone la ruta y la velocidad (siempre dentro del límite) para parar menos. Ciudad piloto: Valladolid.

## Módulos

| Módulo | Contenido |
| --- | --- |
| `core` | Kotlin puro: detección de paradas en semáforos, estimación de ciclo/desfase, rutas dependientes de la hora, política de velocidad (tope legal, radares), ola verde |
| `backend` | API Ktor: subida de viajes, aprendizaje de semáforos, semáforos y radares por zona. PostgreSQL (o H2 en local) |
| `app` | Android (Compose): mapa con el recorrido en vivo (MapLibre + OpenFreeMap), grabación a 1 Hz en CSV, lista de viajes con duración, km y paradas |

## Backend

```
./gradlew :backend:run                                       # API en :8080 con H2 local
./gradlew :backend:run --args="fetch-osm valladolid.json"    # descarga OSM (Overpass)
OSM_FILE=valladolid.json DATABASE_URL=jdbc:postgresql://localhost/ov \
  DATABASE_USER=ov DATABASE_PASSWORD=ov ./gradlew :backend:run
```

| Endpoint | Qué hace |
| --- | --- |
| `POST /v1/trips` | Sube un viaje (CSV, cabecera `X-Device-Id`). Detecta rojos/verdes y guarda solo el resumen y las observaciones, no la traza |
| `GET /v1/signals?bbox=minLon,minLat,maxLon,maxLat&t=ms` | Semáforos de la zona con su modelo (ciclo, desfase, rojo, confianza) para la franja de `t` |
| `GET /v1/cameras?bbox=…` | Radares de la zona |

El workflow «Descargar OSM de Valladolid» genera el JSON de OSM cada mes o a mano.

## Comandos

```
./gradlew :core:test          # tests del núcleo (no necesita SDK Android)
./gradlew :app:assembleDebug  # APK de depuración (necesita SDK Android)
```

El APK de cada PR se descarga desde los artefactos de GitHub Actions.

## Pendiente en fase 1

- Navegación con la ruta propuesta y avisos por voz.
