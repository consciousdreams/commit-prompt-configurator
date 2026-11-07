package it.consciousdreams;

import com.google.gson.JsonParser;
import org.jdom.Element;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.jar.JarEntry;
import java.util.jar.JarOutputStream;

import static org.junit.Assert.assertEquals;

public class AirPromptTest {

    private static final String AIR_DEFAULT = "Air default instruction";

    @Rule public TemporaryFolder temporaryFolder = new TemporaryFolder();

    @Test
    public void readsDefaultFromAirBundle() throws Exception {
        Path pluginPath = temporaryFolder.newFolder().toPath();
        Path jarPath = pluginPath.resolve("lib/modules/intellij.air.shared.settings.jar");
        Files.createDirectories(jarPath.getParent());
        try (JarOutputStream jar = new JarOutputStream(Files.newOutputStream(jarPath))) {
            jar.putNextEntry(new JarEntry("messages/AgentCommitMessageSettingsBundle.properties"));
            jar.write(("instruction.default=" + AIR_DEFAULT + "\n").getBytes(java.nio.charset.StandardCharsets.UTF_8));
            jar.closeEntry();
        }

        assertEquals(AIR_DEFAULT, AirPrompt.defaultPrompt(pluginPath));
    }

    @Test
    public void readsAirCustomPromptAndPreservesOtherSettings() throws Exception {
        Element root = new Element("project");
        Element component = new Element("component");
        component.setAttribute("name", "AgentCommitMessageProject");
        component.setText("{\"mode\":\"CUSTOM\",\"instruction\":\"old\",\"futureField\":true}");
        root.addContent(component);

        assertEquals("old", AirPrompt.read(root, AIR_DEFAULT));
        String prompt = "new\nmessage 🎉 🐛 📝 🧹 🏗️ 🔁";
        AirPrompt.write(root, prompt, AIR_DEFAULT);

        assertEquals(prompt, AirPrompt.read(root, AIR_DEFAULT));
        assertEquals(true, JsonParser.parseString(component.getText()).getAsJsonObject()
                .get("futureField").getAsBoolean());
    }

    @Test
    public void resetUsesAirDefaultMode() throws Exception {
        Element root = new Element("project");
        AirPrompt.write(root, AIR_DEFAULT, AIR_DEFAULT);

        Element component = root.getChild("component");
        assertEquals("DEFAULT", JsonParser.parseString(component.getText()).getAsJsonObject()
                .get("mode").getAsString());
        assertEquals(AIR_DEFAULT, AirPrompt.read(root, AIR_DEFAULT));
    }
}
