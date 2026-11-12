package com.roottap.clicker.resources

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import javax.xml.parsers.DocumentBuilderFactory
import org.w3c.dom.Element

class StringResourcesTest {
    private val moduleRoots = listOf(File("src/main/res"), File("../kit/src/main/res"))
    private val locales = listOf("values-zh", "values-ru")
    private val placeholder = Regex("%\\d+\\$[a-zA-Z]")

    private fun strings(file: File): Map<String, String> {
        val nodes = DocumentBuilderFactory.newInstance()
            .newDocumentBuilder()
            .parse(file)
            .getElementsByTagName("string")
        return (0 until nodes.length)
            .map { nodes.item(it) as Element }
            .filter { it.getAttribute("translatable") != "false" }
            .associate { it.getAttribute("name") to it.textContent }
    }

    @Test
    fun everyLocaleDefinesTheSameKeys() {
        for (root in moduleRoots) {
            val base = strings(File(root, "values/strings.xml"))
            assertTrue("No strings in $root", base.isNotEmpty())
            for (locale in locales) {
                val translated = strings(File(root, "$locale/strings.xml"))
                assertEquals("Keys differ in $root/$locale", base.keys, translated.keys)
            }
        }
    }

    @Test
    fun everyLocaleUsesTheSamePlaceholders() {
        for (root in moduleRoots) {
            val base = strings(File(root, "values/strings.xml"))
            for (locale in locales) {
                val translated = strings(File(root, "$locale/strings.xml"))
                for ((name, value) in base) {
                    assertEquals(
                        "Placeholders differ in $locale/$name",
                        placeholder.findAll(value).map { it.value }.sorted().toList(),
                        placeholder.findAll(translated.getValue(name)).map { it.value }.sorted().toList(),
                    )
                }
            }
        }
    }
}
