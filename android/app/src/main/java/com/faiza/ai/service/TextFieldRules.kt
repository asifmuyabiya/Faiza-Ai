package com.faiza.ai.service

/** Which screen elements Faiza can write into (pure, unit-tested). */
object TextFieldRules {
    private val TEXT_CLASSES = listOf("EditText", "AutoCompleteTextView", "SearchView\$SearchAutoComplete", "TextInputEditText")

    /**
     * A box takes typing when Android says it's editable, when it is an edit-text class, or when it accepts the
     * "set text" action (web pages and custom apps often don't say "editable").
     */
    fun isTextField(className: String?, editable: Boolean, canSetText: Boolean): Boolean {
        if (editable || canSetText) return true
        val cls = className.orEmpty().substringAfterLast('.')
        return TEXT_CLASSES.any { cls == it || cls.endsWith(it) }
    }
}
