package it.consciousdreams;

import com.intellij.openapi.application.WriteAction;
import com.intellij.openapi.util.JDOMUtil;
import com.intellij.openapi.vfs.VirtualFile;
import org.jdom.Element;
import org.jdom.JDOMException;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

final class AiAssistantPrompt {

    static final String PROMPT_ID = "AIAssistant.VCS.GenerateCommitMessage";

    private static final String STORAGE_COMPONENT = "AIAssistantCustomInstructionsStorage";
    private static final String XML_OPTION = "option";
    private static final String XML_ENTRY = "entry";
    private static final String XML_VALUE = "value";
    private static final String XML_NAME = "name";
    private static final String XML_COMPONENT = "component";
    private static final String XML_MAP = "map";
    private static final String XML_KEY = "key";
    private static final String XML_CONTENT = "content";
    private static final String XML_ACTION_ID = "actionId";
    private static final String XML_INSTRUCTIONS = "instructions";
    private static final String XML_STORED_INSTRUCTION = "AIAssistantStoredInstruction";

    private AiAssistantPrompt() {}

    @Nullable
    static String read(VirtualFile workspaceFile) throws IOException, JDOMException {
        Element root = JDOMUtil.load(workspaceFile.contentsToByteArray());
        Element component = findStorageComponent(root);
        if (component == null) return null;
        return extractContentValue(component);
    }

    static void write(VirtualFile workspaceFile, String prompt) throws IOException, JDOMException {
        Element root = JDOMUtil.load(workspaceFile.contentsToByteArray());

        Element component = findStorageComponent(root);
        if (component == null) {
            component = new Element(XML_COMPONENT);
            component.setAttribute(XML_NAME, STORAGE_COMPONENT);
            root.addContent(component);
        }

        ensureEntryExists(component, prompt);

        // Prepend XML declaration that IntelliJ expects when reading workspace.xml back
        String xml = "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n" + JDOMUtil.write(root);
        byte[] bytes = xml.getBytes(StandardCharsets.UTF_8);
        WriteAction.run(() -> workspaceFile.setBinaryContent(bytes));
    }

    @Nullable
    private static Element findStorageComponent(Element root) {
        for (Element child : root.getChildren(XML_COMPONENT)) {
            if (STORAGE_COMPONENT.equals(child.getAttributeValue(XML_NAME))) return child;
        }
        return null;
    }

    @Nullable
    private static String extractContentValue(Element storageComponent) {
        Element instructions = findOption(storageComponent, XML_INSTRUCTIONS);
        if (instructions == null) return null;
        Element map = instructions.getChild(XML_MAP);
        if (map == null) return null;
        for (Element entry : map.getChildren(XML_ENTRY)) {
            if (!PROMPT_ID.equals(entry.getAttributeValue(XML_KEY))) continue;
            Element value = entry.getChild(XML_VALUE);
            if (value == null) return null;
            Element storedInstruction = value.getChild(XML_STORED_INSTRUCTION);
            if (storedInstruction == null) return null;
            Element contentOption = findOption(storedInstruction, XML_CONTENT);
            return contentOption != null ? contentOption.getAttributeValue(XML_VALUE) : null;
        }
        return null;
    }

    private static void ensureEntryExists(Element storageComponent, String promptText) {
        Element instructionsOption = findOption(storageComponent, XML_INSTRUCTIONS);
        if (instructionsOption == null) {
            instructionsOption = new Element(XML_OPTION);
            instructionsOption.setAttribute(XML_NAME, XML_INSTRUCTIONS);
            storageComponent.addContent(instructionsOption);
        }

        Element map = instructionsOption.getChild(XML_MAP);
        if (map == null) {
            map = new Element(XML_MAP);
            instructionsOption.addContent(map);
        }

        Element entry = null;
        for (Element e : map.getChildren(XML_ENTRY)) {
            if (PROMPT_ID.equals(e.getAttributeValue(XML_KEY))) {
                entry = e;
                break;
            }
        }
        if (entry == null) {
            entry = new Element(XML_ENTRY);
            entry.setAttribute(XML_KEY, PROMPT_ID);
            map.addContent(entry);
        }

        Element value = entry.getChild(XML_VALUE);
        if (value == null) {
            value = new Element(XML_VALUE);
            entry.addContent(value);
        }

        Element storedInstruction = value.getChild(XML_STORED_INSTRUCTION);
        if (storedInstruction == null) {
            storedInstruction = new Element(XML_STORED_INSTRUCTION);
            value.addContent(storedInstruction);
        }

        setOption(storedInstruction, XML_ACTION_ID, PROMPT_ID);
        setOption(storedInstruction, XML_CONTENT, promptText);
    }

    @Nullable
    private static Element findOption(Element parent, String name) {
        for (Element child : parent.getChildren(XML_OPTION)) {
            if (name.equals(child.getAttributeValue(XML_NAME))) return child;
        }
        return null;
    }

    private static void setOption(Element parent, String name, String attrValue) {
        Element option = findOption(parent, name);
        if (option == null) {
            option = new Element(XML_OPTION);
            option.setAttribute(XML_NAME, name);
            parent.addContent(option);
        }
        option.setAttribute(XML_VALUE, attrValue);
    }
}
