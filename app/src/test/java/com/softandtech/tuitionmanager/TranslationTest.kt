package com.softandtech.tuitionmanager

import java.io.File
import javax.xml.parsers.DocumentBuilderFactory
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.w3c.dom.Element

/**
 * Keeps translations in step with English: every string and plural must exist, and use the same
 * placeholders (order may differ — Hindi word order often does). A missing or mistyped
 * placeholder would crash the app or show the wrong amount at run time.
 */
class TranslationTest {

    private val res = File("src/main/res")
    private val translations = listOf("values-hi")

    private fun load(dir: String): Map<String, List<String>> {
        val doc = DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(File(res, "$dir/strings.xml"))
        val result = mutableMapOf<String, List<String>>()
        val strings = doc.getElementsByTagName("string")
        for (i in 0 until strings.length) {
            val e = strings.item(i) as Element
            result[e.getAttribute("name")] = listOf(e.textContent)
        }
        val plurals = doc.getElementsByTagName("plurals")
        for (i in 0 until plurals.length) {
            val e = plurals.item(i) as Element
            val items = e.getElementsByTagName("item")
            result["plurals:" + e.getAttribute("name")] = (0 until items.length).map { items.item(it).textContent }
        }
        return result
    }

    private fun placeholders(text: String): Set<String> =
        Regex("""%(\d+\$)?[sd]""").findAll(text).map { it.value }.toSet()

    @Test
    fun everyStringIsTranslatedWithMatchingPlaceholders() {
        val english = load("values")
        assertTrue("Sanity: English strings loaded", english.size > 200)

        translations.forEach { dir ->
            val translated = load(dir)
            val missing = english.keys - translated.keys
            assertEquals("$dir is missing strings", emptySet<String>(), missing)
            val extra = translated.keys - english.keys
            assertEquals("$dir has strings that no longer exist in English", emptySet<String>(), extra)

            english.forEach { (key, texts) ->
                val expected = texts.flatMap(::placeholders).toSet()
                val actual = translated.getValue(key).flatMap(::placeholders).toSet()
                assertEquals("$dir/$key placeholders", expected, actual)
            }
        }
    }
}
