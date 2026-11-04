# Inicio: mapa con pin, segmentos por distancia y dictado de 30 s

## Header
Funcionalidad de Inicio pedida por el ciudadano: pin buscable en el mapa con
contadores, lista en dos segmentos ordenados por cercanía, nombre de la
ubicación y dictado de hasta 30 segundos. Ratificación enriquecida queda fuera
hasta decidir su forma exacta.

## Specs
- S1: "en la pagina principal en el mapa deberia poder colocar un pin para saber la ubicacion buscanda"
- S2: "que abajo diga la cantidad de reportes ratificados y la cantidad de reportes pendientes"
- S3: "en la tab de Lista deberia de mostrar en dos segmentos los reportes ratificados y los reportes pendientes de ratificar ordenadas ambas listas del mas cercano al mas lejano de acuerdo al mapa"
- S4: "el mapa tambien debe tener el boton mi ubicacion y colocar el pin"
- S5: "en la lista mostrar arriba el nombre de la ubicacion seleccionada segun google maps, si no hay seleccion ponlo en el parque central de guatemala"
- S6: "Dictar por voz ... permita grabar ... maximo 30 segundos porque se corta muy rapido pero si no habla que se cancele solito o si deja mucho tiempo sin hablar ... solo amplialo a 30 segundos siempre manteniendo el comportamiendo de Detener"
- S7 (pendiente de decisión): enriquecer ratificación desde pendientes (título/descripción/categoría/ubicación/fotos) y relajar el proceso.

## Tasks
- T1 GeoUtils + test (centro por defecto, haversine, formato distancia, orden)
- T2 FeedScreenModel: punto del mapa, nombre, GPS, listas ordenadas (S1-S5)
- T3 ReportMap: pin + botón mi ubicación (android/ios) (S1, S4)
- T4 FeedScreen UI: contadores, segmentos, nombre arriba (S2, S3, S5)
- T5 Dictado 30 s + corte silencioso (android/ios/modelos) (S6)
- T6 Compilación + probes + reinstall + prueba en dispositivo
- T7 Ratificación enriquecida (bloqueado por decisión S7)

## Decisiones tomadas sin el usuario
- Contadores del mapa: Ratificados = publicados, Por ratificar = cola de la zona.
- Zona sigue al punto (mapa/GPS); el texto manual solo sobrevive si no hay dato.
- Verificar adopta texto limpio con aviso; solo seguridad bloquea.
- Ratificaciones muestran su conteo en cada fila de la lista.

## Log
- L1: petición original del usuario (ver Specs S1-S7).
- L2: decisión S7 del usuario: panel simple enriquecido (título/categoría/ubicación prellenados del reporte, foto obligatoria, solo registrados de la zona).
- L3: verificado en compilación + probes JVM (GeoUtils 5/5, etiquetas 10/10); pendiente prueba en dispositivo.
