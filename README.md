# Ola Verde

App Android que aprende los ciclos de los semáforos con el GPS de tus viajes y propone la ruta y la velocidad (siempre dentro del límite) para parar menos. Ciudad piloto: Valladolid.

## Módulos

| Módulo | Contenido |
| --- | --- |
| `core` | Kotlin puro: detección de paradas en semáforos, estimación de ciclo/desfase, rutas dependientes de la hora, política de velocidad (tope legal, radares), ola verde |
| `app` | Android (Compose): grabación de viajes en segundo plano a 1 Hz, guardados en CSV |

## Comandos

```
./gradlew :core:test          # tests del núcleo (no necesita SDK Android)
./gradlew :app:assembleDebug  # APK de depuración (necesita SDK Android)
```

El APK de cada PR se descarga desde los artefactos de GitHub Actions.

## Pendiente en fase 1

- Backend (API + PostgreSQL/PostGIS) para subir viajes y guardar modelos.
- Grafo de calles de Valladolid desde OpenStreetMap, semáforos y radares.
- Navegación con la ruta propuesta y avisos por voz.
