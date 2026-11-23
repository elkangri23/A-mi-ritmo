# A mi ritmo — borrador para Google Play (español)

**Estado:** borrador interno para revisión; no copiar a Play Console ni publicar todavía.  
**Bloqueos de publicación:** falta aceptar el flujo completo en dispositivos, configurar una firma de producción, confirmar la clasificación de Data safety con el comportamiento de Google Play services/Wear OS Data Layer, completar identidad y contacto del desarrollador, alojar la política de privacidad en una URL pública y preparar los recursos gráficos.

## Ficha de Play Store

Google Play limita el nombre a 30 caracteres, la descripción breve a 80 y la descripción completa a 4.000. Este borrador está dentro de esos límites, según la [guía oficial de fichas](https://support.google.com/googleplay/android-developer/answer/9859152).

**Nombre**  
A mi ritmo

**Descripción breve**  
Lee guiones en el móvil y controla el ritmo desde tu reloj Wear OS.

**Descripción completa**

A mi ritmo convierte documentos y notas en guiones que puedes revisar, preparar y leer desde el teléfono. Coloca el móvil en el atril y controla la lectura desde la pantalla o, con un reloj Wear OS compatible, sin tocar el teléfono.

**Prepara tu guion**

- Importa PDF con texto seleccionable, Word (.doc y .docx), OpenDocument (.odt), Markdown (.md), texto (.txt) y HTML local (.htm y .html), o escribe notas en el teléfono.
- Revisa y edita el título, las secciones, los párrafos, las listas, las tablas, las pausas y los énfasis antes de guardar.
- Al importar listas anidadas, la sangría se aplana y la numeración puede reiniciarse. Revisa el resultado antes de guardar.
- Ajusta colores, tamaño de letra, interlineado y velocidad.
- Guarda y organiza guiones preparados en una carpeta local del teléfono, o exporta su archivo JSON para trasladarlo a otro dispositivo.

**Lee y controla la lectura**

- Sigue el texto bajo una guía fija y conserva localmente el último punto de lectura en ese teléfono.
- Inicia con cuenta atrás, pausa, cambia la velocidad, avanza entre secciones, rebobina o vuelve al inicio.
- El reloj Wear OS puede controlar la reproducción, la velocidad y las secciones, y mostrar el avance y el tiempo restante.

La importación, edición y lectura en el teléfono funcionan sin conexión. El control con el reloj necesita que ambos dispositivos puedan comunicarse; Wear OS Data Layer puede retransmitir metadatos de reproducción mediante servicios de Google si Bluetooth no está disponible. El texto, el título y las notas del guion no se envían al reloj.

Los PDF escaneados o basados en imágenes no incluyen texto extraíble; la app no incorpora OCR ni reconocimiento de escritura a mano. La extracción de archivos .doc es de solo texto y puede no conservar su formato. Revisa el resultado importado antes de usarlo en una presentación.

La app de teléfono requiere Android 11 o posterior. El control remoto requiere la app complementaria en un reloj Wear OS compatible.

## Política de privacidad — texto de trabajo

> Sustituir los campos entre corchetes y revisar Data safety antes de alojar esta política. Google Play exige que la política identifique al desarrollador, incluya un contacto, describa los datos tratados, su uso, divulgación, seguridad y retención, y esté disponible desde la app y en una URL pública activa. Véase la [política oficial de datos de usuario](https://support.google.com/googleplay/android-developer/answer/10144311).

### Política de privacidad de A mi ritmo

**Responsable:** [NOMBRE LEGAL O IDENTIDAD DEL DESARROLLADOR]  
**Contacto de privacidad y soporte:** [CORREO DE CONTACTO]  
**Fecha de entrada en vigor:** [FECHA]

#### Datos que maneja la app

A mi ritmo no requiere una cuenta. Cuando eliges un documento con el selector del sistema, la app lee el archivo seleccionado y procesa su contenido en el teléfono para crear un guion editable. El archivo original no se modifica. Algunos importadores usan archivos temporales en la caché durante el procesamiento; el archivo temporal del importador ODT se elimina al finalizar la extracción.

Los guiones preparados se guardan como archivos JSON en la carpeta local que eliges mediante el selector de Android. Incluyen el texto que preparaste, su estructura, énfasis, ajustes de apariencia y velocidad. La posición de lectura —incluido un fragmento breve del texto alrededor del marcador para restaurarlo— se guarda localmente en los datos privados de la app y no forma parte del JSON exportado.

Si conectas un reloj y abres un guion, ambos dispositivos intercambian metadatos temporales para mostrar y controlar la sesión: estado, sección e identificadores internos de guion/sección, avance, velocidad, tiempo restante, identificadores de sesión/comando, números de secuencia y acciones del control. No se envían el título, los subtítulos, el texto, las notas ni las señales de pausa. Google Play services Wearable transporta estos mensajes por Bluetooth cifrado cuando existe esa conexión o puede retransmitirlos por la nube de Google mediante cifrado de extremo a extremo. La app no mantiene un servidor propio ni conserva estos mensajes como historial. Consulta también las condiciones y la política de privacidad de Google para los servicios de Google Play.

A mi ritmo no opera un servidor propio, no requiere iniciar sesión y no integra anuncios ni analítica propia. Esta descripción debe volver a revisarse si se añaden SDK, permisos o servicios.

#### Uso, conservación y eliminación

El contenido importado se usa para crear el guion preparado y mostrarlo durante la lectura. Los archivos preparados permanecen en la carpeta que elegiste hasta que los elimines o los muevas. Eliminar un guion desde la biblioteca también elimina la posición de lectura local asociada. Desinstalar la app elimina sus datos privados, incluida la posición local; los archivos JSON guardados en la carpeta compartida del teléfono pueden permanecer y deben eliminarse desde la biblioteca o el gestor de archivos.

No se crea una cuenta de usuario. Para consultas de privacidad, solicita ayuda en [CORREO DE CONTACTO].

#### Almacenamiento y seguridad

La app usa el selector de documentos de Android para acceder a los archivos que eliges y la carpeta que autorizas. La posición de lectura se guarda en el almacenamiento privado de la app. Los mensajes de control del reloj usan Wear OS Data Layer. No afirmamos que los JSON exportados estén cifrados: se guardan en la carpeta local que selecciona el usuario y su protección depende de la seguridad y configuración del dispositivo.

#### Cambios de esta política

Publicaremos cualquier cambio en [URL PÚBLICA DE ESTA POLÍTICA] y actualizaremos la fecha de entrada en vigor.

## Revisión preliminar de Data safety

No es una respuesta final para Play Console. La declaración debe describir el comportamiento de la app y de todos sus SDK, incluidas transmisiones a servicios de terceros; la [guía oficial de Data safety](https://support.google.com/googleplay/android-developer/answer/10787469) define la recopilación como transmisión de datos desde el dispositivo y exige revisar bibliotecas y SDK.

| Flujo observado | Tratamiento actual | Punto por resolver antes de contestar Data safety |
|---|---|---|
| Documento seleccionado o nota compartida | Se procesa en el teléfono y se convierte en guion preparado local | Confirmar que ningún SDK transmite el documento y mantener la declaración actualizada si cambian dependencias. |
| Guion JSON preparado | Se guarda en la carpeta local elegida; el usuario puede exportarlo por Android | El usuario controla cualquier exportación posterior mediante el destino que elige. |
| Ancla de lectura | Preferencias privadas locales con identificadores de bloque y contexto breve de texto | Confirmar la duración de datos al desinstalar en dispositivos compatibles; no es una sincronización de nube implementada por la app. |
| Estado y órdenes de reproducción al reloj | `MessageClient` intercambia snapshots y órdenes efímeros; incluye estado, sección/IDs internos, avance, tiempo restante, velocidad, IDs de sesión/comando, secuencia y acción. Sin título ni contenido del guion. Bluetooth usa canal cifrado; sin Bluetooth, Data Layer puede retransmitir por nube Google con cifrado de extremo a extremo. | La clasificación no está cerrada: revisar si los metadatos/acciones encajan en App activity (App interactions) y si los IDs efímeros activan Device or other IDs; confirmar si aplican las excepciones de procesamiento efímero, cifrado de extremo a extremo y/o proveedor de servicios. La respuesta corresponde al desarrollador en Play Console. |
| Cuenta, publicidad, analítica propia | No hay cuenta, anuncios ni SDK de analítica propios en la implementación revisada | Reauditar manifiestos combinados y dependencias antes de cada lanzamiento. |

### Evidencia técnica para Data safety (auditoría local 2026-11-23)

- Los manifiestos combinados de Release de teléfono y Wear no declaran `android.permission.INTERNET` ni permisos peligrosos; sí incluyen el permiso interno de AndroidX para receptores dinámicos. Esto describe el permiso de la app, pero **no** descarta una transferencia mediada por Google Play services.
- El protocolo `WearMessage` no tiene campos para título, contenido, notas ni cues. `PlaybackState` sí contiene identificadores internos de guion/sección, estado, ordinal de sección, velocidad, progreso y tiempo restante. `PlaybackCommand`/ACK incluyen UUIDs de sesión/comando, secuencia, acción, resultado y revisión. Son metadatos de reproducción y control, no el guion.
- La app usa `MessageClient` y envía mensajes a nodos conectados. La documentación oficial dice que los mensajes son pequeños/efímeros, privados a la misma app y que el éxito de envío solo indica que se encoló, no que haya llegado. Wear OS Data Layer usa Bluetooth cuando está conectado o nube de Google si no; el canal Bluetooth está cifrado y el transporte por nube usa cifrado de extremo a extremo. Fuentes: [Wear OS Data Layer](https://developer.android.com/training/wearables/data/overview), [MessageClient](https://developers.google.com/android/reference/com/google/android/gms/wearable/MessageClient).
- Google Play exige que se revise también el tráfico de SDK de terceros. La guía admite excepciones para datos procesados de manera efímera, ciertos proveedores de servicios y transferencias cifradas de extremo a extremo; esta auditoría no determina por sí sola qué casilla corresponde a la sesión teléfono-reloj. Antes de contestar Data safety, el desarrollador debe clasificar los metadatos según las categorías y propósitos vigentes de Play y confirmar la aplicabilidad de las excepciones. Fuentes: [formulario de Data safety para desarrolladores](https://support.google.com/googleplay/android-developer/answer/10787469), [definiciones visibles para usuarios](https://support.google.com/googleplay/answer/11416267).
- Se resolvieron los árboles completos `releaseRuntimeClasspath` de teléfono y Wear y se buscaron dependencias de Firebase, Analytics, Crashlytics, Ads/Advertising ID, Adjust, Appsflyer, Sentry, Datadog, Mixpanel, Amplitude, Telemetry e Install Referrer: no aparecieron en ninguno de los dos grafos. El subárbol de Google Play services es `play-services-wearable:20.0.1` más `play-services-base:18.5.0`, `play-services-basement:18.9.0` y `play-services-tasks:18.2.0`; las demás dependencias observadas son AndroidX/Compose, Kotlin y los parsers/documentación. La [declaración de Google para Data safety](https://developers.google.com/android/guides/play-data-disclosure) dice que los SDKs base, basement, OSS Licenses y tasks no recopilan datos en las versiones que cubre, pero no cubre `play-services-wearable`; no extrapolar esa afirmación al Data Layer. Revisar esta información y el comportamiento específico de la versión de Wearable antes de enviar el formulario; reauditar dependencias/manifiestos y el tráfico de los artefactos firmados.
## Recursos y campos pendientes

- Nombre del desarrollador y correo de contacto de soporte/privacidad.
- URL HTTPS pública y no editable para esta política; enlace desde la app.
- Confirmación de la clasificación de Data safety, incluida la biblioteca Google Play services Wearable.
- Borradores gráficos de Play Store creados: [icono PNG de 512 × 512](assets/a-mi-ritmo-play-icon-512.png) y su [SVG](assets/a-mi-ritmo-play-icon.svg), más el [gráfico de funciones PNG de 1024 × 500](assets/a-mi-ritmo-feature-graphic-draft-1024x500.png). Ambos cumplen el formato/dimensiones técnicas y siguen pendientes de aprobación visual. Para la ficha, preparar de 2 a 8 capturas de teléfono (cuatro verticales 1080 × 1920 para la recomendación de alta resolución) y al menos una captura Wear OS cuadrada sin marco ni elementos superpuestos; consulta el [plan de tomas](play_store_screenshot_plan_es.md) y sus fuentes oficiales.
- Añadir el factor de forma Wear OS y sus capturas a la misma ficha de Google Play; completar el flujo de publicación de ese factor, que admite un AAB o APK Wear según la configuración de Play Console ([guía oficial](https://support.google.com/googleplay/android-developer/answer/13295490)).
- Revisión de esta ficha después de completar QA; no prometer disponibilidad de funciones que no hayan sido aceptadas en dispositivos.

## Configuración de firma para builds locales

Los módulos `phone` y `wear` comparten una configuración opcional de firma Release. Antes de configurar el build, proporciona las mismas cuatro variables de entorno en el proceso Gradle:

- `AMR_RELEASE_STORE_FILE`
- `AMR_RELEASE_STORE_PASSWORD`
- `AMR_RELEASE_KEY_ALIAS`
- `AMR_RELEASE_KEY_PASSWORD`

Si las cuatro están presentes, ambos artefactos usan esa misma identidad de firma. Si todas están ausentes, el build conserva el comportamiento actual y genera AAB sin firmar. Si falta alguna, Gradle se detiene para evitar firmar solo un factor de forma. La clave y sus contraseñas no se guardan en el repositorio. Antes de publicar, elegir la configuración de firma de Play Console, fijar una estrategia de custodia y comprobar que phone y Wear usan el mismo certificado de subida.



