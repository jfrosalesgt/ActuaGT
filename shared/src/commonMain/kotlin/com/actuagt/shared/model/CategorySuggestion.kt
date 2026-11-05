package com.actuagt.shared.model

// Keyword-based category suggestion from the report wording. Returns the
// category code; the UI maps it to its chips and the user can always pick
// another one by hand. Matching ignores case and accents and scans title
// plus description in priority order, falling back to OTROS.
fun suggestCategoryCode(title: String, description: String): String {
    val haystack = normalize("$title $description")
    return CATEGORY_KEYWORDS.firstOrNull { (_, keywords) ->
        keywords.any { haystack.contains(it) }
    }?.first ?: "OTROS"
}

private fun normalize(text: String): String {
    var out = text.lowercase()
    ACCENTS.forEach { (accented, plain) -> out = out.replace(accented, plain) }
    return out
}

private const val ACCENTED = "áéíóúüñ"
private const val PLAIN = "aeiouun"

private val ACCENTS: List<Pair<String, String>> =
    ACCENTED.mapIndexed { index, c -> c.toString() to PLAIN[index].toString() }

private val CATEGORY_KEYWORDS: List<Pair<String, List<String>>> = listOf(
    "VIALIDAD" to listOf(
        "bache", "hoyo", "hueco", "calle", "avenida", "asfalto", "acera",
        "banqueta", "cuneta", "semaforo", "senal", "tope", "tumulo",
        "trafico", "carretera", "puente", "peatonal", "carril",
    ),
    "AGUA_Y_DRENAJES" to listOf(
        "agua", "drenaje", "alcantarilla", "tuberia", "fuga",
        "inundacion", "charco", "desague", "cloaca", "caneria", "grifo",
    ),
    "ALUMBRADO" to listOf(
        "luz", "alumbrado", "poste", "lampara", "foco", "farol",
        "oscuridad", "oscuro", "oscura", "noche", "bombilla", "led",
        "luminaria", "apagon",
    ),
    "DESECHOS_Y_AMBIENTE" to listOf(
        "basura", "desecho", "desperdicio", "contenedor", "vertedero",
        "clandestino", "quema", "humo", "olor", "hedor", "sucio", "sucia",
        "maleza", "monte", "plaga", "rata", "raton", "mosca", "zancudo",
        "ripio", "escombro",
    ),
    "ESPACIOS_PUBLICOS" to listOf(
        "parque", "plaza", "cancha", "juegos", "banca", "monumento",
        "fuente", "jardin", "arbol", "grafiti", "columpio",
    ),
)
