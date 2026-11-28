package io.lifeos.focuslab

import android.os.Build
import java.text.Normalizer
import java.util.Locale

object AppDisplayOrder {
    fun <T> ordered(items: List<T>, name: (T) -> String, pkg: (T) -> String): List<T> {
        if (Build.VERSION.SDK_INT < 29) return FocusPresentation.ordered(items, name, pkg)
        return try {
            val ids = android.icu.text.Transliterator.getAvailableIDs().toList()
            if ("Han-Latin" !in ids) return FocusPresentation.ordered(items, name, pkg)
            val translator = android.icu.text.Transliterator.getInstance("Han-Latin")
            FocusPresentation.ordered(items, name, pkg) { label ->
                Normalizer.normalize(translator.transliterate(label), Normalizer.Form.NFD)
                    .replace(Regex("\\p{M}"), "").filterNot { it.isWhitespace() }.lowercase(Locale.ROOT)
            }
        } catch (_: Exception) { FocusPresentation.ordered(items, name, pkg) }
    }
}
