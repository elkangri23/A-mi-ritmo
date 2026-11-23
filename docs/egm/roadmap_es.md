# Hoja de ruta de A mi ritmo

**Objetivo:** publicar en Google Play la app de teléfono y su complemento Wear OS.  
**Estado de referencia:** revisión 0.1.0 r29 · 23 de noviembre de 2026.

## Entregas

| Fase | Resultado | Estado |
|---|---|---|
| 1. Base compartida | Modelo portable de guion, JSON versión 1, estado y órdenes de reproducción, ancla de posición y herramientas Kotlin/Gradle. | Implementada; existe prueba unitaria del códec JSON. |
| 2. Biblioteca e importación en teléfono | Seleccionar carpeta local, importar formatos, revisar, guardar y transferir guiones JSON sin conexión. | App y flujos principales implementados. Validar fidelidad en documentos reales y dispositivos Samsung. |
| 3. Lector de teléfono | Lectura en atril, secciones, marcador fijo, reproducción, pausa, velocidad, cuenta atrás, posición local y pantalla activa. | Implementado y ejercitado en AVD API 30; legibilidad en atril y revisión física pendientes. |
| 4. Mando Wear OS | App independiente de reloj con Data Layer, estado confirmado, ACK de órdenes y control de lectura. | Implementado; la pantalla sin conexión se verificó en AVD Wear OS 3. Emparejamiento, reintentos y reconexión en Watch7 físico pendientes. |
| 5. Build de revisión | APK debug de teléfono y Wear, pruebas de aceptación, evidencia y aprobación interna. | r29 instalado en AVD API 30; el escenario de reloj desconectado pasa. La configuración inicial del teléfono y la revisión física siguen abiertas. |
| 6. Publicación | Firma, artefactos de publicación, ficha, privacidad, Data safety, capturas finales y revisión de Google Play. | No iniciada como lanzamiento público; quedan bloqueos previos. |

## Criterios de salida pendientes

1. Instalar el mismo paquete r29 en teléfono compatible y Watch7; registrar modelos, versiones, firma y resultados en el [checklist de QA](../release/wear_device_qa_checklist_es.md).
2. Aceptar controles de reproducción, pasos y límites de velocidad, navegación en los extremos, rebobinado al mantener pulsado, vuelta al inicio, reconexión y comportamiento offline.
3. Completar revisión de importación con documentos representativos y confirmar que el texto y el formato se pueden corregir antes de guardarlos.
4. Verificar recuperación del marcador y lectura prolongada en un teléfono real, con la pantalla encendida y accesibilidad suficiente para un atril.
5. Crear artefactos Release/AAB con una estrategia de firma Play y comprobar que los módulos de teléfono y Wear conservan identidad compatible.
6. Completar Data safety, política de privacidad pública, identidad y contacto del desarrollador, y recursos gráficos.
7. Capturar las interfaces finales en hardware aceptado, incluida una sesión activa del reloj. Sustituir las imágenes internas de emulador.
8. Enviar la ficha y los factores de forma a Google Play para la revisión del desarrollador y de la tienda.

## Dependencias y alcance

- La lectura la gobierna el teléfono; el reloj envía órdenes y muestra un resumen de estado. El texto y las notas no se transfieren al reloj.
- Los guiones y la biblioteca son locales. La importación y lectura en teléfono no requieren Internet.
- La prueba física de Wear depende de que ambos dispositivos tengan la misma revisión y firma y puedan comunicarse por Wear OS Data Layer.
- El HTML estático de boceto sirve como referencia, no como app de producción.
- OCR, reconocimiento de escritura a mano, sincronización propia en nube y seguimiento por voz quedan fuera del primer lanzamiento acordado.

## Referencias

- [Resumen en español del producto y decisiones](resumen_es.md)
- [Backlog resumido en español](backlog_es.md)
- [Guía de usuario](../usuario/guia_usuario_es.md)
- [Compilación e instalación](../desarrollo/compilar_instalar_es.md)
- [Paquete de revisión de Google Play](../release/play_store_review_pack_es.md)
