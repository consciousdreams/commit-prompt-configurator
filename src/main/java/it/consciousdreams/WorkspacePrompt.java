package it.consciousdreams;

import com.intellij.openapi.application.WriteAction;
import com.intellij.openapi.util.JDOMUtil;
import com.intellij.openapi.vfs.VirtualFile;
import org.jdom.Element;
import org.jdom.JDOMException;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

final class WorkspacePrompt {

    static final String PROMPT_ID = "AIAssistant.VCS.GenerateCommitMessage";

    private static final String STORAGE_COMPONENT = "AIAssistantCustomInstructionsStorage";
    private static final String XML_OPTION = "option";
    private static final String XML_ENTRY = "entry";
    private static final String XML_VALUE = "value";
    private static final String XML_INSTRUCTIONS = "instructions";
    private static final String XML_STORED_INSTRUCTION = "AIAssistantStoredInstruction";

    private WorkspacePrompt() {}

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
            component = new Element("component");
            component.setAttribute("name", STORAGE_COMPONENT);
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
        for (Element child : root.getChildren("component")) {
            if (STORAGE_COMPONENT.equals(child.getAttributeValue("name"))) return child;
        }
        return null;
    }

    @Nullable
    private static String extractContentValue(Element storageComponent) {
        Element instructions = findOption(storageComponent, XML_INSTRUCTIONS);
        if (instructions == null) return null;
        Element map = instructions.getChild("map");
        if (map == null) return null;
        for (Element entry : map.getChildren(XML_ENTRY)) {
            if (!PROMPT_ID.equals(entry.getAttributeValue("key"))) continue;
            Element value = entry.getChild(XML_VALUE);
            if (value == null) return null;
            Element storedInstruction = value.getChild(XML_STORED_INSTRUCTION);
            if (storedInstruction == null) return null;
            Element contentOption = findOption(storedInstruction, "content");
            return contentOption != null ? contentOption.getAttributeValue(XML_VALUE) : null;
        }
        return null;
    }

    private static void ensureEntryExists(Element storageComponent, String promptText) {
        Element instructionsOption = findOption(storageComponent, XML_INSTRUCTIONS);
        if (instructionsOption == null) {
            instructionsOption = new Element(XML_OPTION);
            instructionsOption.setAttribute("name", XML_INSTRUCTIONS);
            storageComponent.addContent(instructionsOption);
        }

        Element map = instructionsOption.getChild("map");
        if (map == null) {
            map = new Element("map");
            instructionsOption.addContent(map);
        }

        Element entry = null;
        for (Element e : map.getChildren(XML_ENTRY)) {
            if (PROMPT_ID.equals(e.getAttributeValue("key"))) {
                entry = e;
                break;
            }
        }
        if (entry == null) {
            entry = new Element(XML_ENTRY);
            entry.setAttribute("key", PROMPT_ID);
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

        setOption(storedInstruction, "actionId", PROMPT_ID);
        setOption(storedInstruction, "content", promptText);
    }

    @Nullable
    private static Element findOption(Element parent, String name) {
        for (Element child : parent.getChildren(XML_OPTION)) {
            if (name.equals(child.getAttributeValue("name"))) return child;
        }
        return null;
    }

    private static void setOption(Element parent, String name, String attrValue) {
        Element option = findOption(parent, name);
        if (option == null) {
            option = new Element(XML_OPTION);
            option.setAttribute("name", name);
            parent.addContent(option);
        }
        option.setAttribute(XML_VALUE, attrValue);
    }
}