package it.consciousdreams;

import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import com.google.gson.JsonParser;
import com.intellij.ide.plugins.PluginManagerCore;
import com.intellij.openapi.application.WriteAction;
import com.intellij.openapi.extensions.PluginId;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.JDOMUtil;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VirtualFile;
import org.jdom.Element;
import org.jdom.JDOMException;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.Properties;

final class AirPrompt {

    public static final String AIR_PLUGIN_ID = "com.intellij.air";
    private static final String COMPONENT_NAME = "AgentCommitMessageProject";
    private static final String SETTINGS_BUNDLE = "messages/AgentCommitMessageSettingsBundle.properties";
    private static final String DEFAULT_INSTRUCTION_PROPERTY = "instruction.default";
    private static final String SETTINGS_FILE = ".idea/vcs.xml";
    private static final String XML_DECLARATION = "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n";
    private static final String COMPONENT_ELEMENT = "component";
    private static final String NAME_ATTRIBUTE = "name";
    private static final String MODE_FIELD = "mode";
    private static final String INSTRUCTION_FIELD = "instruction";
    private static final String DEFAULT_MODE = "DEFAULT";
    private static final String CUSTOM_MODE = "CUSTOM";

    private AirPrompt() {}

    static boolean isActive() {
        return PluginManagerCore.isLoaded(PluginId.getId(AIR_PLUGIN_ID));
    }

    static String defaultPrompt() throws IOException {
        if (!isActive()) {
            throw new IOException("Air is not active.");
        }
        InputStream stream = AirPrompt.class.getClassLoader().getResourceAsStream(SETTINGS_BUNDLE);
        if (stream == null) throw new IOException("Cannot find Air's commit message settings bundle.");
        Properties properties = new Properties();
        try (InputStreamReader reader = new InputStreamReader(stream, StandardCharsets.UTF_8)) {
            properties.load(reader);
        }
        String prompt = properties.getProperty(DEFAULT_INSTRUCTION_PROPERTY);
        if (prompt == null || prompt.isEmpty()) {
            throw new IOException("Air's default commit message prompt is empty.");
        }
        return prompt;
    }

    @Nullable
    static String read(Project project, String defaultPrompt) throws IOException, JDOMException {
        VirtualFile file = findSettingsFile(project);
        if (file == null) return null;

        return read(JDOMUtil.load(file.contentsToByteArray()), defaultPrompt);
    }

    static void write(Project project, String prompt, String defaultPrompt) throws IOException, JDOMException {
        VirtualFile file = findSettingsFile(project);
        if (file == null) throw new IOException("Cannot locate Air's " + SETTINGS_FILE + " settings file.");

        Element root = JDOMUtil.load(file.contentsToByteArray());
        write(root, prompt, defaultPrompt);

        String xml = XML_DECLARATION + JDOMUtil.write(root);
        byte[] bytes = xml.getBytes(StandardCharsets.UTF_8);
        WriteAction.run(() -> file.setBinaryContent(bytes));
    }

    @Nullable
    static String read(Element root, String defaultPrompt) throws IOException {
        Element component = findComponent(root);
        if (component == null || component.getText().isBlank()) return null;

        JsonObject state = parseState(component.getText());
        if (state.has(MODE_FIELD) && CUSTOM_MODE.equals(state.get(MODE_FIELD).getAsString())) {
            return state.has(INSTRUCTION_FIELD) ? state.get(INSTRUCTION_FIELD).getAsString() : "";
        }
        return defaultPrompt;
    }

    static void write(Element root, String prompt, String defaultPrompt) throws IOException {
        Element component = findComponent(root);
        if (component == null) {
            component = new Element(COMPONENT_ELEMENT);
            component.setAttribute(NAME_ATTRIBUTE, COMPONENT_NAME);
            root.addContent(component);
        }

        JsonObject state = component.getText().isBlank()
                ? new JsonObject()
                : parseState(component.getText());
        boolean isDefault = defaultPrompt.equals(prompt);
        state.addProperty(MODE_FIELD, isDefault ? DEFAULT_MODE : CUSTOM_MODE);
        state.addProperty(INSTRUCTION_FIELD, isDefault ? "" : prompt);
        component.setText(state.toString());
    }

    private static JsonObject parseState(String json) throws IOException {
        try {
            return JsonParser.parseString(json).getAsJsonObject();
        } catch (JsonParseException | IllegalStateException ex) {
            throw new IOException("Invalid Air commit message settings in " + SETTINGS_FILE, ex);
        }
    }

    @Nullable
    private static VirtualFile findSettingsFile(Project project) {
        String basePath = project.getBasePath();
        if (basePath == null) return null;
        return LocalFileSystem.getInstance().refreshAndFindFileByPath(basePath + "/" + SETTINGS_FILE);
    }

    @Nullable
    private static Element findComponent(Element root) {
        for (Element child : root.getChildren(COMPONENT_ELEMENT)) {
            if (COMPONENT_NAME.equals(child.getAttributeValue(NAME_ATTRIBUTE))) return child;
        }
        return null;
    }
}
