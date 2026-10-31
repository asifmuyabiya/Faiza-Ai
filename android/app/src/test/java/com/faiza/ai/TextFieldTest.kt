package com.faiza.ai

import com.faiza.ai.service.TextFieldRules
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Faiza must see every place she can write into (owner: Code Studio's prompt box was "not a text box"). */
class TextFieldTest {
    @Test fun editableBoxes() {
        assertTrue(TextFieldRules.isTextField("android.widget.EditText", editable = true, canSetText = true))
        assertTrue(TextFieldRules.isTextField("android.widget.EditText", editable = false, canSetText = false))
        assertTrue(TextFieldRules.isTextField("com.google.android.material.textfield.TextInputEditText", false, false))
        assertTrue(TextFieldRules.isTextField("android.widget.AutoCompleteTextView", false, false))
    }

    @Test fun webAndCustomFieldsThatOnlyAcceptText() {
        assertTrue(TextFieldRules.isTextField("android.view.View", editable = false, canSetText = true))
        assertTrue(TextFieldRules.isTextField(null, editable = true, canSetText = false))
    }

    @Test fun plainLabelsAndButtonsAreNotBoxes() {
        assertFalse(TextFieldRules.isTextField("android.widget.TextView", false, false))
        assertFalse(TextFieldRules.isTextField("android.widget.Button", false, false))
        assertFalse(TextFieldRules.isTextField(null, false, false))
    }
}
