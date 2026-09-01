package com.errorpurifier;

import org.junit.jupiter.api.Test;
import org.w3c.dom.Document;
import org.w3c.dom.Element;

import javax.xml.parsers.DocumentBuilderFactory;
import java.io.InputStream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;

class PluginXmlLocalizationTest {

    @Test
    void actionAndToolWindowUseBundleAndPluginIconResources() throws Exception {
        DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
        factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
        Document document;
        try (InputStream stream = resource("META-INF/plugin.xml")) {
            document = factory.newDocumentBuilder().parse(stream);
        }

        assertEquals("messages.ErrorPurifierBundle",
                document.getElementsByTagName("resource-bundle").item(0).getTextContent().trim());

        Element action = (Element) document.getElementsByTagName("action").item(0);
        assertEquals("PurifyErrorLogAction", action.getAttribute("id"));
        assertFalse(action.hasAttribute("text"));
        assertFalse(action.hasAttribute("description"));

        Element toolWindow = (Element) document.getElementsByTagName("toolWindow").item(0);
        assertEquals("/icons/errorPurifierToolWindow.svg", toolWindow.getAttribute("icon"));
        try (InputStream ignored = resource("icons/errorPurifierToolWindow.svg")) {
            assertNotNull(ignored);
        }
        try (InputStream ignored = resource("icons/errorPurifierToolWindow_dark.svg")) {
            assertNotNull(ignored);
        }
    }

    private InputStream resource(String path) {
        InputStream stream = getClass().getClassLoader().getResourceAsStream(path);
        assertNotNull(stream, "Missing resource: " + path);
        return stream;
    }
}
