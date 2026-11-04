# Cómo instalar Mapheim

## 1. Compilar las apps (una sola vez, gratis)
1. Crea una cuenta en github.com y un repositorio nuevo (puede ser privado).
2. Sube todo el contenido de esta carpeta al repositorio (botón **Add file → Upload files**, arrastra todo).
3. Abre la pestaña **Actions**. Se ejecutan dos compilaciones solas:
   - **Compilar APK** → descarga `Mapheim-apk` (Android).
   - **Compilar iOS (.ipa)** → descarga `Mapheim-ipa` (iPhone).
4. Descomprime el archivo descargado y tendrás `app-debug.apk` o `Mapheim.ipa`.

## 2a. Android
1. Manda `app-debug.apk` por WhatsApp, Drive o correo.
2. Ábrelo en el teléfono y acepta "instalar apps desconocidas".

## 2b. iPhone sin Mac (gratis, con Sideloadly en Windows)
Necesitas una PC con Windows 10 u 11 y el cable del iPhone.

1. En la PC instala **iTunes desde la web de Apple** (no la versión de Microsoft Store) y **Sideloadly** desde sideloadly.io.
2. Conecta el iPhone por cable. En el iPhone toca **Confiar**.
3. Abre Sideloadly, arrastra `Mapheim.ipa`, escribe un Apple ID (puede ser el de tu amigo) y presiona **Start**.
4. En el iPhone:
   - Ajustes → Privacidad y seguridad → **Modo de desarrollador** → activar (se reinicia).
   - Ajustes → General → **VPN y gestión de dispositivos** → toca el correo del Apple ID → **Confiar**.
5. Abre Mapheim, toca **Explorar** y permite la ubicación.

Límites de la vía gratuita:
- La app deja de abrir a los **7 días**. Se vuelve a instalar igual con Sideloadly (el progreso se conserva si usas el mismo Apple ID).
- Máximo 3 apps instaladas así por Apple ID.
- La fecha y hora de la PC y del iPhone deben estar correctas.

## 2c. iPhone con TestFlight (sin reinstalar cada semana)
Requiere la cuenta **Apple Developer** (99 USD al año). Con ella:
- La app dura hasta 90 días por versión y tu amigo solo toca un enlace de invitación.
- Se puede automatizar desde GitHub; pídele a Claude que agregue el flujo de TestFlight cuando tengas la cuenta.

## Consejos
- Android: Ajustes → Apps → Mapheim → Batería → **Sin restricciones**, para que no se detenga con la pantalla apagada.
- iPhone: cuando pregunte, elige **Permitir mientras se usa la app**. Mientras exploras verás un indicador azul arriba: es normal y significa que sigue registrando tu recorrido.
