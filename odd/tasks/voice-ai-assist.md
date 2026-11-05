# voice-ai-assist — dictado con IA + verificación de coherencia

## Header

El dictado por voz actual es de un solo disparo: graba hasta el silencio, no tiene botón de detener, solo llena la descripción y no verifica las fotos. Esta funcionalidad lo convierte en un asistente: detener manual, título + descripción ordenados por Muse y bloqueo de envío si las fotos no coinciden con lo relatado.

## Specs

- S1: El usuario pidió: botón visible de dictar por voz con una breve explicación de qué hace.
- S2: El usuario pidió: "el app con la ia de muse integrada interprete el audio y ordene la idea y coloque el titulo y la descripcion".
- S3: El usuario pidió: "con la carga de las imagenes analice la imagen con la ia de muse y compare si la situacion reportada coincide con las imagenes si es asi permite continuar de lo contrario no".
- S4: El usuario reportó: "parece que graba, no sale un boton de terminar o algo similar sino que hasta que se deja de hablar al rato para". Debe existir Detener explícito.
- S5: El diálogo de confirmación actual ("Confirmá el texto" con Usar/Editar/Descartar) se conserva y muestra título + descripción.

## Tasks

- [x] T1 (functions): `enhanceReportText` v2 con título+descripción; fixtures y tests. (S2)
- [x] T2 (functions): callable `verifyReportCoherence` + helpers puros y tests. (S3)
- [x] T3 (KMP): `NeutralizedText.Clean(title, text)`, `verifyReportCoherence` en repositorio + `CoherenceVerdict`, `VoiceInput.stopListening()` con actuals Android (stop real) e iOS (sin-op). (S2, S4)
- [x] T4 (KMP): `ReportScreenModel`/`FeedScreenModel` con detener, confirmación de título+descripción y bloqueo de envío ante mismatch; fake de androidTest al día. (S2, S3, S4, S5)
- [x] T5 (KMP): UI con explicación breve, botón Detener y diálogo de confirmación con título. (S1, S5)
- [x] T6: verificación (npm 53/53, compilación Gradle, smoke en dispositivo: dictado→Detener→reposo sin crash, feed y tabs OK).

## Log

- L1: solicitud verbatim del usuario: dictado con IA que interprete el audio, ordene la idea, coloque título y descripción; con imágenes cargadas, analizar si la situación coincide y permitir continuar solo en ese caso; hoy graba sin botón de terminar y no llena nada; pide botón de dictar con breve explicación.
- L2: el dictado no llenaba nada por dos causas: max_tokens cortaba la respuesta de Muse (subido a 4000 con reintento) y los mensajes de error quedaban bajo el fold (mensaje visible arriba + "Ordenando las ideas, espera un momento" durante la llamada). Cámara: MIUI niega el intent sin permiso CAMERA en runtime (se pide antes de capturar).
