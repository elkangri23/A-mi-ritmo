# Compilar, instalar y probar A mi ritmo

## Entorno

- Android Studio con Android SDK Platform 37 y herramientas de compilación instaladas.
- JDK 17.
- Windows: usar `gradlew.bat` desde PowerShell o la terminal de Android Studio. En macOS/Linux, usar `./gradlew`.
- El proyecto contiene tres módulos: `shared` (modelo, formato JSON y protocolo), `phone` (app de teléfono) y `wear` (app de reloj).

El wrapper está fijado a Gradle 9.5.0 en `gradle/wrapper/gradle-wrapper.properties`; al compilar por primera vez, Gradle puede descargar esa distribución y dependencias.

## Compilación local

Desde la raíz del proyecto:

```powershell
\.\gradlew.bat projects
\.\gradlew.bat test
\.\gradlew.bat assembleDebug
```

`test` ejecuta las pruebas unitarias disponibles. Actualmente hay una prueba del códec JSON del módulo `shared`; no equivale a una prueba completa de interfaz, importadores ni conexión teléfono-reloj.

Los APK de depuración se generan aquí:

```text
phone/build/outputs/apk/debug/phone-debug.apk
wear/build/outputs/apk/debug/wear-debug.apk
```

Para crear artefactos de revisión, sigue la convención del paquete de distribución que se describe en `dist/review/A-mi-ritmo-0.1.0-r29/LEEME.txt`; después actualiza sus hashes SHA-256. Los APK de `build/outputs/apk/release/` que existen en el workspace son de release **sin firmar** y no son instalables/publicables como versiones de tienda.

## Instalación en dispositivos conectados por ADB

Habilita las opciones de desarrollador y depuración ADB en cada dispositivo. Con el teléfono conectado:

```powershell
adb devices -l
adb -s <SERIAL_TELEFONO> install -r phone/build/outputs/apk/debug/phone-debug.apk
```

En el reloj, activa ADB y depuración inalámbrica desde las opciones de desarrollador. Para usar ADB inalámbrico, ambos deben estar en una red accesible; sigue el diálogo de emparejamiento de Android:

```powershell
adb pair <IP_RELOJ>:<PUERTO_DE_EMPAREJAMIENTO>
adb connect <IP_RELOJ>:<PUERTO_ADB>
adb devices -l
adb -s <IP_RELOJ>:<PUERTO_ADB> install -r wear/build/outputs/apk/debug/wear-debug.apk
```

Los puertos de emparejamiento y depuración aparecen en la pantalla **Depuración inalámbrica** del reloj y pueden ser distintos. También se puede usar Android Studio y el asistente de emparejamiento Wear OS. Para la prueba funcional, empareja además el Watch7 con el teléfono mediante Galaxy Wearable y abre ambas apps.

Los APK de la distribución r29 ya están compilados y listos para revisión en `dist/review/A-mi-ritmo-0.1.0-r29/`. Comprueba su integridad con:

```powershell
Get-FileHash dist/review/A-mi-ritmo-0.1.0-r29/A-mi-ritmo-telefono-debug.apk -Algorithm SHA256
Get-FileHash dist/review/A-mi-ritmo-0.1.0-r29/A-mi-ritmo-reloj-debug.apk -Algorithm SHA256
```

Los valores de referencia se encuentran en el archivo `SHA256SUMS.txt` del paquete. Ambos APK de una revisión comparten el identificador `com.teleprompter.amiritmo` y deben conservar el mismo certificado para instalarse como pareja. La versión indicada en Gradle es 0.1.0; los códigos son 1 para teléfono y 1000001 para Wear.

## Versiones y dispositivos

| Destino | `minSdk` | API objetivo | Referencia práctica |
|---|---:|---:|---|
| Teléfono | 30 (Android 11) | 36 | Android 11 o posterior. El Samsung Galaxy S8 con Android 9 no cumple. |
| Wear OS | 30 | 35 | Wear OS 3 o posterior según el mínimo del checklist de QA. Galaxy Watch7 es el reloj de referencia; falta documentar una aceptación física. |

Ambos módulos se compilan con `compileSdk = 37` y Java 17. El Galaxy A13 varía según la variante y su actualización instalada: confirma Android en **Ajustes > Acerca del teléfono > Información de software**. Android 17 en el S23 Ultra es un dato facilitado por su propietario y todavía requiere prueba de instalación/funcionamiento en ese dispositivo.

## Plan de prueba

### Pruebas automatizadas disponibles

```powershell
\.\gradlew.bat test
```

Hay una prueba unitaria en `shared/src/test/kotlin/com/teleprompter/shared/serialization/ScriptJsonCodecTest.kt`. Las pruebas de aceptación funcional deben quedar registradas por separado.

### Smoke test en emulador

1. Arranca un AVD de teléfono con API 30 o posterior y un emulador Wear OS.
2. Instala ambos APK de la misma compilación.
3. En el teléfono, configura la carpeta de guiones y crea un guion de prueba con varias secciones.
4. Comprueba que se puede abrir la biblioteca, revisar el guion y entrar al lector.
5. Comprueba visualmente la lectura, el marcador, los controles en pantalla y el mantenimiento de pantalla activa.
6. En el emulador Wear, verifica la presentación del estado y la disponibilidad de controles cuando haya sesión.

El emulador permite comprobar pantallas y parte del flujo de desarrollo. No certifica emparejamiento real, comportamiento de Galaxy Wearable, reconexión, consumo, ni instalación en modelos Samsung.

### Prueba de aceptación con Galaxy Watch7

Usa el [checklist de QA en teléfono y Watch7](../release/wear_device_qa_checklist_es.md). Registra modelo, versiones exactas de Android/Wear OS, versión de Galaxy Wearable, revisión de los APK y resultados. Incluye sesión activa, Play/Pausa, velocidades límite, navegación por secciones, vuelta al inicio, rebobinado manteniendo el botón, desconexión y reconexión, y modo sin Internet. No marques la prueba como aprobada sin evidencia capturada en reloj y teléfono físicos.

## Firma y publicación

Las versiones debug usan el certificado de depuración. Los módulos release aceptan estas variables de entorno para compartir una clave de firma local:

```text
AMR_RELEASE_STORE_FILE
AMR_RELEASE_STORE_PASSWORD
AMR_RELEASE_KEY_ALIAS
AMR_RELEASE_KEY_PASSWORD
```

Se deben configurar las cuatro juntas o dejar todas sin configurar; con ninguna configurada el proyecto produce salidas release sin firmar. No guardes claves ni contraseñas en Git. Antes de Play, prepara la firma/subida de Play App Signing, valida AAB de ambos factores de forma, completa Data safety, política de privacidad, recursos gráficos y QA físico. La lista de bloqueos está en el [paquete de revisión de Play](../release/play_store_review_pack_es.md).

## Referencias oficiales

- [Depurar una app Wear OS por Wi-Fi](https://developer.android.com/training/wearables/get-started/debug-wifi)
- [Depurar apps Wear OS](https://developer.android.com/training/wearables/get-started/debugging)
- [Opciones de desarrollador de Android](https://developer.android.com/studio/debug/dev-options)
