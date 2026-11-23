# A mi ritmo — plan de capturas de Google Play

**Estado:** guion de producción; aún no son capturas de publicación.

## Requisitos vigentes de Google Play

- **Teléfono:** subir de 2 a 8 capturas PNG o JPEG, de hasta 8 MB cada una; cada lado debe medir entre 320 y 3840 px y la proporción máxima no debe superar 2,3:1. Para optar a superficies de recomendación que usan capturas, Google pide al menos cuatro capturas de apps con resolución mínima 1080 px, en 9:16 vertical (1080 × 1920) o 16:9 horizontal (1920 × 1080). Este plan prevé cuatro capturas verticales de 1080 × 1920 px.
- **Wear OS:** incluir al menos una captura cuadrada 1:1 de 384 × 384 px o más. Debe mostrar solo la interfaz actual de la app: sin marco de dispositivo ni texto, gráficos o fondos añadidos; no usar transparencia ni máscaras.
- **Icono de ficha:** PNG de 512 × 512 px con alfa, máximo 1024 KB. El [borrador actual](assets/a-mi-ritmo-play-icon-512.png) está en PNG de 32 bits, pesa 13 KB y sigue pendiente de aprobación visual.
- **Gráfico de funciones:** obligatorio para publicar; JPEG o PNG de 24 bits sin alfa, 1024 × 500 px. Hay un [borrador provisional](assets/a-mi-ritmo-feature-graphic-draft-1024x500.png) en PNG de 24 bits; sigue pendiente de revisión y aprobación.

Fuentes: [requisitos de capturas, Wear OS, icono y gráfico de funciones](https://support.google.com/googleplay/android-developer/answer/9866151) y [configuración de la ficha principal](https://support.google.com/googleplay/android-developer/answer/9859152).

## Criterios de captura

- Capturar la compilación que haya superado QA de teléfono y Watch7, con la versión y el paquete registrados.
- Usar contenido ficticio, breve y legible; no incluir nombres, documentos, notificaciones ni datos personales.
- Capturar teléfono y reloj en hardware real para representar sus interfaces. Las imágenes del emulador sirven solo para revisión interna.
- En la captura Wear OS, recortar al lienzo cuadrado de la interfaz real sin marco, rotulación superpuesta, fondo añadido, transparencia ni máscara.
- Mantener los controles visibles y el texto dentro del área segura; evitar recortar el indicador de lectura o las etiquetas de sección.
- Confirmar los tamaños y formatos admitidos en Play Console cuando se prepare la ficha. No reutilizar capturas si cambia la interfaz o la versión aceptada.

## Tomas previstas

| Orden | Dispositivo y pantalla | Contenido que debe verse | Texto promocional propuesto | Estado |
|---|---|---|---|---|
| 1 | Teléfono — biblioteca | Un guion ficticio guardado localmente y las opciones para importar o escribir notas | «Tus guiones, listos para leer» | Pendiente de captura aceptada |
| 2 | Teléfono — revisión del guion | Secciones con subtítulos, énfasis y una señal de pausa, antes de guardar | «Prepara y revisa cada sección» | Pendiente de captura aceptada |
| 3 | Teléfono — lector en el atril | Texto legible, guía fija, marcador y sección actual; sin diálogo superpuesto | «Lee a tu ritmo desde el atril» | Pendiente de captura aceptada |
| 4 | Teléfono — controles de lectura | Controles de reproducción, velocidad, navegación de sección y tiempo restante | «Ajusta la lectura cuando lo necesites» | Pendiente de captura aceptada |
| 5 | Watch7 — control remoto conectado | Teléfono y reloj con sesión activa; sección, avance y estado confirmado; una orden visible en el reloj | «Controla la lectura desde el reloj» | Requiere QA emparejado; no capturar antes |

## Exclusiones antes de publicar

- No presentar una pantalla desconectada del reloj como si demostrara control remoto activo.
- No usar capturas de emulador como material final ni afirmar compatibilidad con un dispositivo que no haya pasado la revisión.
- No incluir la pantalla vacía de primera ejecución como toma principal de la ficha.
- No añadir afirmaciones de OCR, sincronización en la nube o funciones que no estén implementadas y aceptadas.

Las capturas finales se archivarán aquí junto a la ficha solo después de aprobar la compilación y confirmar el flujo real en S23 Ultra y Watch7.

Las capturas internas de la biblioteca del teléfono y del reloj sin sesión están en `../usuario/capturas/`; documentan el estado del prototipo y no son recursos de ficha. Las capturas exploratorias del selector de carpetas son registros internos y no forman parte de la documentación publicada. Sustituye las capturas internas por tomas de dispositivos reales después de aceptar la versión.
