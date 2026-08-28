package com.repforth.core.model

/**
 * The two languages the product ships (§1, §13).
 *
 * Neither is a translation of the other and both are required; an exercise
 * missing either one fails the import. Modelled as an enum precisely so that
 * adding a third language is a compile error at every point that handles
 * language, rather than a silently missing string.
 */
enum class Language(val tag: String) {
    ENGLISH("en"),
    TURKISH("tr");

    companion object {
        fun fromTag(tag: String): Language? = entries.firstOrNull { it.tag == tag }
    }
}

/**
 * Instruction text in both languages. There is no "default" language and no
 * fallback: if a translation is missing the record should not have been
 * imported, so the type does not allow expressing the absence.
 */
data class LocalizedInstructions(
    private val byLanguage: Map<Language, InstructionText>,
) {
    init {
        val missing = Language.entries.filterNot { it in byLanguage }
        require(missing.isEmpty()) {
            "Missing instructions for: ${missing.joinToString { it.tag }}"
        }
    }

    operator fun get(language: Language): InstructionText = byLanguage.getValue(language)
}

/** One language's instructions: a short summary plus ordered steps. */
data class InstructionText(
    val summary: String,
    val steps: List<String>,
)
