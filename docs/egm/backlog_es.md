# Backlog de A mi ritmo

Este documento presenta el backlog actual en español. Las prioridades `P0` señalan trabajo necesario para la primera entrega de revisión/publicación; “pendiente” significa que no se debe tratar como aceptación final aunque ya haya código o comprobaciones en emulador.

## Base compartida

| ID | Prioridad | Estado | Trabajo | Criterio de aceptación |
|---|---|---|---|---|
| FND-01 | P0 | Hecho | Crear proyecto Kotlin/Gradle con módulos `shared`, `phone` y `wear`. | Wrapper fijado, compilación y estructura reproducibles. |
| FND-02 | P0 | Hecho | Definir modelo portable del guion y bloques estructurados. | Título, secciones, énfasis, pausas, listas y tablas serializables. |
| FND-03 | P0 | Hecho | Añadir códec JSON versionado y validación. | Leer/escribir versión compatible, rechazar contenido inválido o versión futura con error claro. |
| FND-04 | P0 | Hecho | Definir órdenes, confirmaciones, estado y ancla del lector. | El teléfono y el reloj comparten contratos y la sesión invalida órdenes antiguas. |
| FND-05 | P0 | Hecho | Fijar toolchain Gradle/JDK. | Wrapper Gradle 9.5; daemon Gradle en JDK 25; toolchain/código Java-Kotlin en JDK 17. |
| FND-06 | P0 | Hecho | Añadir prueba automatizada del contrato JSON. | Prueba unitaria en `shared/src/test`; ampliar cobertura antes de publicación según riesgo. |

## Biblioteca e importación del teléfono

| ID | Prioridad | Estado | Trabajo | Criterio de aceptación |
|---|---|---|---|---|
| PHN-01 | P0 | Hecho | Crear biblioteca de guiones y notas compartidas. | Abrir, importar, exportar, borrar guiones JSON y recibir texto compartido como borrador. |
| PHN-02 | P0 | Hecho en AVD; Samsung pendiente | Elegir carpeta de destino con Storage Access Framework. | Usar `Documents/TelePrompter`, conservar permiso local y rechazar ubicaciones fuera del alcance. |
| PHN-03 | P0 | En revisión | Importar PDF, DOC/DOCX, ODT, Markdown, TXT y HTML. | Mostrar borrador editable y avisos específicos de fidelidad; conservar texto legible y limpiar temporales. Comprobar documentos variados y memoria en teléfonos reales. |
| PHN-04 | P0 | En revisión | Revisar y editar contenido antes de guardar. | Editar título, secciones, texto, listas, celdas de tabla, pausas, énfasis y aspecto; validar campos vacíos. |
| PHN-05 | P0 | Hecho en pruebas manuales | Guardar, importar y exportar JSON. | Leer guion offline, exportarlo e importarlo en un segundo teléfono, manteniendo contenido/estructura. La posición de lectura queda fuera del archivo portable. |

## Lector del teléfono

| ID | Prioridad | Estado | Trabajo | Criterio de aceptación |
|---|---|---|---|---|
| RDR-01 | P0 | Implementado; dispositivo pendiente | Mostrar texto con guía fija, marcador, sección, progreso y tiempo. | Legibilidad desde atril y marcador inequívoco en teléfonos compatibles. |
| RDR-02 | P0 | Implementado; dispositivo pendiente | Cuenta atrás, play/pausa, velocidad, rebobinado corto, secciones e inicio. | Probar límites 0,5×–2,0×, cambios de sección y parada al final en hardware real. |
| RDR-03 | P0 | Implementado; regresión pendiente | Guardar y recuperar la posición del teléfono. | Reabrir en bloque y sección correctos; documentar respaldo si el contenido cambió. |
| RDR-04 | P0 | Implementado; revisión visual pendiente | Representar pausas, tablas y listas en el lector. | Que pausas sean distinguibles y filas de tablas se puedan leer en formato estrecho. |
| RDR-05 | P0 | Implementado; accesibilidad pendiente | Mantener pantalla activa, dar recordatorio y ofrecer etiquetas accesibles. | No atenuar durante lectura; revisar contraste, escala de texto, TalkBack y Samsung. |

## Complemento Wear OS

| ID | Prioridad | Estado | Trabajo | Criterio de aceptación |
|---|---|---|---|---|
| WAT-01 | P0 | Pantalla desconectada verificada en AVD Wear OS 3; Watch7 pendiente | Pantalla remota compacta con conexión y resumen. | Estado desconectado claro y disposición legible en reloj redondo; validar Watch7 real. |
| WAT-02 | P0 | Código implementado; transporte pendiente de emparejamiento real | Sincronizar sesión, snapshots, ACK, identificadores y secuencias con Data Layer. | Probar descubrimiento, entrega, orden y sesión en teléfono + reloj físicos. |
| WAT-03 | P0 | Implementado; controles físicos pendientes | Play/pausa, velocidad, sección anterior/siguiente, inicio y rebobinado mantenido. | La acción se confirma en teléfono y se detiene al soltar el control de rebobinado. |
| WAT-04 | P0 | Implementado; prueba física pendiente | Recuperar sesión al desconectar/reconectar y caducar órdenes de rebobinado. | No repetir órdenes de sesiones antiguas; resincronizar el estado actual. |

## Revisión y publicación

| ID | Prioridad | Estado | Trabajo | Criterio de aceptación |
|---|---|---|---|---|
| QA-01 | P0 | Abierto | Probar el flujo completo en teléfono Samsung y Watch7. | Checklist completo, incidencias reproducibles y build aceptado por la persona responsable. |
| QA-02 | P0 | Abierto | Probar desconexión, reconexión y estado offline. | Biblioteca local opera sin Internet; mandos Wear se recuperan de forma segura. |
| REL-01 | P0 | Abierto | Definir firma de publicación y construir artefactos Release/AAB. | Clave protegida, identidad coherente entre teléfono y Wear; no incluir contraseñas en Git. |
| REL-02 | P0 | Abierto | Completar ficha, política de privacidad y Data safety. | Declaraciones y contacto coherentes con servicios/datos que usa el producto. |
| REL-03 | P0 | Abierto | Capturar imágenes finales de la app. | Capturas reales de teléfono y Watch7 de una compilación aceptada; recursos cumplen los requisitos vigentes de Play. |
| REL-04 | P0 | Abierto | Preparar y enviar publicación de Google Play. | Revisión de factores de forma, ficha completa y aprobación de Google Play. |

## Pendiente fuera del primer lanzamiento

OCR de documentos escaneados, reconocimiento de escritura a mano, sincronización de guiones en nube, seguimiento por voz y otras plataformas móviles. No comunicar estas funciones como disponibles.
