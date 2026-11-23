# A mi ritmo — checklist de QA en teléfono y Watch7

Usa este registro para cerrar QA-01, WAT-01–04 y los escenarios de reconexión de QA-02. El paquete actual de revisión es `dist/A-mi-ritmo-review-0.1.0-r29.zip`; los APK son de depuración y todavía no están firmados para Google Play. Los hashes de los APK y los recursos incluidos están en `SHA256SUMS.txt`, dentro del ZIP.

El paquete contiene los APK de teléfono y Wear, un icono y un gráfico de funciones de Play como borradores pendientes de aprobación. Comprueba que ambos APK proceden de la misma revisión y comparten certificado antes de instalar.

## Dispositivos y build

- Teléfono / modelo exacto: ____________________
- Android / número de compilación / RAM: ____________________
- Reloj / modelo exacto: ____________________
- Wear OS / versión de Galaxy Wearable: ____________________
- APK o ZIP instalado: ____________________
- Certificado de firma coincidente en ambos APK: ☐ Sí ☐ No
- Fecha y persona que ejecuta la revisión: ____________________

## Preparación

- Instalar el APK de teléfono y el APK Wear del mismo paquete de revisión. Ambos deben compartir `com.teleprompter.amiritmo` y certificado.
- Emparejar el Watch7 con el teléfono mediante Galaxy Wearable. Abrir A mi ritmo en ambos.
- Guardar en el teléfono un guion de revisión con tres secciones: Apertura, Desarrollo y Cierre; texto suficiente para que el lector tarde al menos un minuto.
- Anotar los valores de Android y Wear OS mostrados en Ajustes. El mínimo de teléfono es Android 11 / API 30; el Galaxy S8 con Android 9 queda fuera.

## Escenarios y resultado esperado

| # | Acción | Resultado que debe observarse | Resultado |
|---|---|---|---|
| 1 | Abrir el reloj sin el teléfono conectado | Mensaje de teléfono no disponible; Play y demás controles desactivados. | ☐ Pasa ☐ Falla |
| 2 | Conectar teléfono y reloj sin abrir un guion | El reloj informa la conexión, pero no habilita controles de reproducción sin sesión de lectura. | ☐ Pasa ☐ Falla |
| 3 | Abrir el guion en el teléfono y empezar a leer | El reloj muestra sección, progreso, tiempo restante y velocidad confirmados por el teléfono. | ☐ Pasa ☐ Falla |
| 4 | Usar Play/Pausa desde el reloj | El estado del teléfono cambia y el reloj muestra confirmación/estado actualizado. | ☐ Pasa ☐ Falla |
| 5 | Subir y bajar velocidad desde el reloj | El teléfono aplica pasos de 0,1×; en límites 0,5×/2,0× no aplica valores fuera del rango y el reloj recibe rechazo/estado actual. | ☐ Pasa ☐ Falla |
| 6 | Ir a sección anterior/siguiente y llegar a ambos extremos | El teléfono navega a la primera línea de la sección destino; en el extremo confirma que no hay sección fuera de rango. | ☐ Pasa ☐ Falla |
| 7 | Pulsar Al inicio desde el reloj | La lectura vuelve al principio y se detiene. | ☐ Pasa ☐ Falla |
| 8 | Mantener «Rebobinar» durante 5–10 s y soltar | El teléfono retrocede mientras se mantiene pulsado y deja de retroceder al soltar; no continúa más allá del vencimiento de la concesión. | ☐ Pasa ☐ Falla |
| 9 | Durante el rebobinado, observar ACKs/estado y mantener pulsado más de 4 s | Las renovaciones no cancelan el reintento de otra orden; una respuesta antigua no reemplaza el estado actual del reloj. | ☐ Pasa ☐ Falla |
| 10 | Cortar conexión mientras una orden está pendiente; volver a conectar | El reloj limpia la sesión antigua, vuelve a obtener el estado y no repite una orden pendiente de la sesión previa. | ☐ Pasa ☐ Falla |
| 11 | Forzar cierre del teléfono durante lectura; reabrir lector y reloj | Cambia la época del teléfono; el reloj se sincroniza con la nueva sesión y los ACK antiguos se ignoran. | ☐ Pasa ☐ Falla |
| 12 | Repetir órdenes con Internet desactivado y Bluetooth activo | La biblioteca y lectura siguen disponibles offline; registrar si el control Wear funciona por enlace local en esta configuración. | ☐ Pasa ☐ Falla |

## Incidencias y evidencia

- Pasos para reproducir: ____________________
- Estado mostrado en teléfono y reloj: ____________________
- ¿La orden se aplicó más de una vez?: ____________________
- Excepción fatal (si existe): ____________________
- Capturas/logs adjuntos: ____________________
- Versión final aceptada: ☐ No ☐ Sí; notas: ____________________

No marques los escenarios 3–12 como aceptados con el reloj desconectado. Conserva los resultados del API 30 AVD como evidencia separada de la prueba física.












