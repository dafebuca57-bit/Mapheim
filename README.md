# Mapheim – Hannover bajo la niebla

Incluye dos apps: Android (carpeta raíz, Kotlin) e iPhone (carpeta `ios`, SwiftUI + MapKit). **Para instalar, lee `INSTALAR.md`.**

App Android tipo Valheim: el mapa empieza cubierto de niebla y se despeja por donde caminas. Los lugares, parques y bosques aparecen al pasar cerca, y hay logros.

## Qué incluye
- Mapa de OpenStreetMap con niebla (celdas de ~50 m que se despejan con el GPS).
- Sigue funcionando con la pantalla bloqueada (servicio en primer plano con notificación).
- Lugares de Hannover: unos 35 escogidos a mano + cientos que se descargan de OpenStreetMap la primera vez que abres la app (monumentos, museos, parques, jardines y bosques).
- 38 monumentos y 32 ciudades del resto de Alemania.
- 36 logros: lugares, zonas verdes, Herrenhausen, casco antiguo, Eilenriede, Maschsee, % de Hannover, km caminados, días, ciudades y monumentos de Alemania.
- Solo suma km a paso de persona (hasta ~16 km/h). En carro o bus la niebla se despeja, pero no cuenta como caminado.
- Estilo vikingo: paneles de madera tallada y bronce, escudos como botones, tipografías Uncial Antiqua y Cinzel, y mapa en tono pergamino.
- Pines propios: comida, casa, tren, bus, Stadtbahn, café, taberna, tienda, baño, parqueo, favorito y nota. Se ponen manteniendo presionado el mapa o con el escudo del pin (en tu ubicación). Al tocarlos puedes ver la nota, abrir "Cómo llegar" o eliminarlos. Menú ⋮ → Mis pines los lista por cercanía.
- Moneda: **Ojos de Greydwarf**.
  - 1 por minuto con la app abierta o explorando.
  - 3 cada 200 m caminados.
  - 10 por lugar, 25 por lugar famoso, 50 por monumento de Alemania, 40 por ciudad nueva.
  - Hazañas: de 50 a 5000 ojos.
- El mercader (escudo del ojo): estilos de mapa (biomas), colores de niebla, marcadores del jugador (antorcha, escudo, piedra rúnica, fuego fatuo, núcleo ardiente) y marcos de pines.
- Modo prueba (menú ⋮): mantén presionado el mapa y elige "simular que estoy aquí".

## Cómo obtener el APK

### Opción A: Android Studio
1. Abre la carpeta `Mapheim` en Android Studio (versión Koala o más reciente).
2. Espera a que termine de sincronizar Gradle.
3. Conecta el teléfono con depuración USB y presiona ▶ Run, o usa **Build → Build APK(s)**.

### Opción B: GitHub (sin instalar nada)
1. Sube esta carpeta a un repositorio de GitHub.
2. Ve a la pestaña **Actions**: se compila solo en unos 5 minutos.
3. Descarga el artefacto **Mapheim-apk**, descomprímelo e instala `app-debug.apk` en el teléfono (permite "instalar apps desconocidas").

## Consejos de uso
- Al tocar **Explorar**, acepta la ubicación precisa y las notificaciones.
- Para que no se detenga con la pantalla apagada: Ajustes → Apps → Mapheim → Batería → **Sin restricciones**.
- Tu progreso se guarda solo en el teléfono.

## Estructura
- `GameState.kt`: progreso, niebla, descubrimientos y guardado.
- `Overlays.kt`: dibujo de la niebla, los lugares y el jugador.
- `TrackingService.kt`: GPS en segundo plano.
- `Curated.kt`: lugares escogidos de Hannover y Alemania.
- `PoiRepository.kt`: descarga de lugares desde OpenStreetMap.
- `Achievements.kt`: lista de logros y sus recompensas.
- `Pins.kt`: pines del usuario.
- `Shop.kt`: catálogo del mercader y dibujo de marcadores y pines.

Fuentes: Uncial Antiqua y Cinzel (licencia SIL Open Font).
