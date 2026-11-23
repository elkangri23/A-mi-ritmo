# A mi ritmo: propósito, decisiones y estado del proyecto

**Documento de consulta en español** · Actualizado el 23 de noviembre de 2026 · Versión de revisión 0.1.0 r29

## Propósito

A mi ritmo es un teleprompter de atril para ponentes. El teléfono sostiene el guion y lo muestra durante la lectura; un reloj Wear OS actúa como mando remoto para que la persona pueda avanzar, pausar o ajustar el ritmo sin tocar el móvil. El marcador de lectura se mantiene visible para ayudar a retomar el punto exacto.

## Personas usuarias y contexto

- Ponentes, formadores y presentadores que leen un guion durante una charla.
- Personas que preparan el material en su teléfono y colocan ese mismo teléfono en un atril.
- Personas con un reloj Wear OS compatible que quieren controlar la lectura con la muñeca.
- El flujo de edición y configuración funciona desde el teléfono; el reloj no sustituye al lector ni muestra el contenido completo.

## Decisiones de producto acordadas

| Tema | Decisión |
|---|---|
| Plataforma | Android para el teléfono y Wear OS para la app complementaria del reloj. |
| Aplicaciones | Dos módulos distribuibles: app de teléfono y app de reloj, con biblioteca y formato de guion compartidos. |
| Primer reloj de referencia | Samsung Galaxy Watch7. La compatibilidad debe cerrarse con QA físico. |
| Versión mínima | `minSdk 30` (Android 11/API 30) para teléfono y Wear; Wear OS 3/API 30 o posterior como mínimo previsto. |
| Lectura | El texto se lee en el teléfono. La pantalla queda activa mientras el lector está abierto. |
| Control del reloj | Reproducir/pausa, velocidad más/menos, sección anterior/siguiente, rebobinar al mantener pulsado y volver al inicio. |
| Reanudación | Guardar el último punto local en el teléfono; al reabrir, intentar volver al marcador guardado. |
| Secciones | Subtítulos claros y navegación directa entre secciones. |
| Preparación | Importar archivo, revisar el borrador, ajustar texto y aspecto y guardar como guion preparado. El original no se modifica. |
| Formatos de entrada | TXT, Markdown, HTML local, PDF con texto seleccionable, Word DOC/DOCX, OpenDocument ODT, notas escritas en la app y JSON preparado. |
| Formato portable | Guion preparado en JSON, para exportar e importar en otro teléfono. La posición de lectura no viaja en ese archivo. |
| Almacenamiento | Carpeta local elegida dentro del teléfono; no requiere cuenta ni Internet para importar, guardar o leer. |
| Formato de lectura | Conservar énfasis y estructura compatible, incluidas tablas y listas cuando el importador puede representarlas. Avisar cuando el formato original se simplifique. |
| Velocidad | Controles más/menos, pasos de 0,1× y límites de 0,5× a 2,0×. |
| Tienda | El objetivo final es publicar teléfono y Wear OS en Google Play después de aprobar revisión y preparación de publicación. |

## Funciones previstas y presentes en 0.1.0

### Preparar guiones

- Crear una nota en el teléfono o importar un documento mediante el selector de Android.
- Revisar título, secciones, párrafos, listas, celdas de tabla, pausas y énfasis antes de guardar.
- Ajustar color, tamaño de letra e interlineado para el atril.
- Guardar en la carpeta local elegida y exportar/importar JSON para trasladar el guion.

### Leer y recuperar la posición

- Abrir un guion en el teléfono, usar cuenta atrás, reproducir, pausar y ajustar el ritmo.
- Mostrar una guía fija y el marcador sobre el texto; cambiar de sección o volver al inicio.
- Guardar la posición localmente y mantener la pantalla activa mientras se lee.
- Mostrar un recordatorio previo a la lectura y avisar de contraste bajo cuando corresponda.

### Controlar desde Wear OS

- Mostrar estado de conexión, sección, porcentaje, tiempo restante y velocidad confirmados por el teléfono.
- Mandar órdenes al lector del teléfono y mostrar el estado actualizado.
- Mantener las órdenes asociadas a la sesión actual; descartar órdenes antiguas al desconectarse o reconectarse.

El detalle de cada flujo se encuentra en la [guía de usuario](../usuario/guia_usuario_es.md), y los criterios de prueba en el [checklist de QA del teléfono y Watch7](../release/wear_device_qa_checklist_es.md).

## Compatibilidad y dispositivos previstos

- **Android 11/API 30 en adelante:** mínimo del teléfono en Gradle. La API objetivo actual es 36.
- **Wear OS API 30 en adelante:** mínimo del módulo Wear. El plan de QA trata Wear OS 3 como mínimo de uso y Watch7 como referencia; el comportamiento real requiere prueba emparejada.
- **Samsung Galaxy S8 con Android 9:** no compatible con el mínimo de Android 11.
- **Samsung Galaxy A13:** confirmar la versión exacta instalada; cumple el mínimo solo si ejecuta Android 11 o posterior.
- **Samsung Galaxy S23 Ultra con Android 17:** por encima del mínimo nominal según la información facilitada; todavía hay que completar QA físico.
- **Samsung Galaxy Watch7:** dispositivo de referencia; todavía debe registrarse la aceptación física del flujo completo.

Consulta [compilar, instalar y probar](../desarrollo/compilar_instalar_es.md) para requisitos de desarrollo y comandos.

## Límites actuales y exclusiones

- No hay OCR integrado: los PDF escaneados o formados por imágenes no se pueden extraer como texto. Tampoco hay reconocimiento de escritura a mano.
- La extracción de `.doc` es texto sin formato; las listas y tablas de formatos complejos pueden perder estructura. Las listas anidadas pueden aplanarse o reiniciar la numeración.
- El control remoto depende de una comunicación disponible entre teléfono y reloj. Hay que probar la conexión y la reconexión con hardware físico.
- No hay sincronización propia en la nube. El archivo JSON se traslada mediante exportación elegida por la persona usuaria.
- El modo de lectura mantiene activa la pantalla, pero no altera el modo de ahorro de energía ni No molestar.

## Estado de entrega

| Área | Estado actual |
|---|---|
| App Android de teléfono | Implementada; build debug disponible; aceptación física Samsung pendiente. |
| App Wear OS | Implementada; estado desconectado probado en emulador; sesión emparejada física pendiente. |
| Importadores | Adaptadores para los formatos enumerados; hay restricciones de fidelidad que deben mostrarse y revisarse. |
| Editor, lector y marcador | Funciones principales implementadas; quedan revisiones en hardware y accesibilidad. |
| Prueba automatizada | Una prueba unitaria del códec JSON del módulo compartido; no cubre todo el sistema. |
| Paquete de revisión | `dist/A-mi-ritmo-review-0.1.0-r29.zip`, con APK debug y borradores gráficos. |
| Google Play | No publicado. Falta cerrar QA físico, firma, declaración de datos, privacidad, capturas y revisión final. |
| Control de versiones | Esta carpeta de trabajo no tiene `.git` ni remoto configurado. Aún no se puede decir que esté subida a Git. |

## Roadmap resumido

1. Completar la prueba de aceptación de teléfono + Watch7 y cerrar incidencias de compatibilidad, marcadores, importación y accesibilidad.
2. Aprobar el build de revisión con resultados de QA y evidencias de dispositivos reales.
3. Preparar la firma de publicación, AAB, ficha de tienda, política de privacidad y formulario Data safety.
4. Capturar las interfaces finales en teléfono y reloj; sustituir las capturas internas de emulador.
5. Publicar una ficha de Google Play para los factores de forma de teléfono y Wear OS tras la revisión del desarrollador y de la tienda.

## Historial de diseño EGM

El workspace de trabajo conserva los documentos fuente y los informes detallados creados durante el proceso de diseño/revisión EGM. La publicación del repositorio incluye este resumen y las guías activas en español; los informes históricos de auditoría quedan fuera del repositorio y no sustituyen el checklist de aceptación.
