# FadulTransf

App Android para compartir archivos entre el teléfono y otros equipos conectados a la misma red Wi-Fi, usando un navegador. Interfaz lila, selección de carpeta, descarga y subida de archivos protegidas por un PIN temporal.

## Compilar en GitHub

1. Creá un repositorio en GitHub y subí el contenido de esta carpeta (incluidos `.github` y `gradle`).
2. En la pestaña **Actions**, esperá a que termine **Compilar FadulTransf**.
3. Abrí esa ejecución, bajá hasta **Artifacts** y descargá **FadulTransf-APK**. Extraé el ZIP para encontrar `app-debug.apk`.

También podés compilar localmente con JDK 17 y Android SDK instalados: `./gradlew assembleDebug`.

## Uso

1. Instalá y abrí la app.
2. Tocá **Elegir carpeta** y seleccioná la carpeta que vas a compartir.
3. Conectá teléfono y computadora a la misma Wi-Fi y tocá **Iniciar servidor**.
4. En el navegador del otro equipo, ingresá la dirección que muestra la app, por ejemplo `http://192.168.1.20:8080`, y el PIN temporal.
5. Descargá archivos del teléfono o subí archivos a la carpeta seleccionada. Tocá **Detener servidor** al terminar.

## Seguridad y notas

El servidor escucha en el puerto 8080 y el PIN cambia en cada inicio. El PIN protege las operaciones de listado, descarga y subida. La página inicial solo muestra el formulario de acceso. Usá redes de confianza; el tráfico local no está cifrado. Android puede restringir la conexión si la red Wi-Fi tiene aislamiento entre dispositivos. El servicio continúa en primer plano mientras compartís; la notificación indica que sigue activo.
