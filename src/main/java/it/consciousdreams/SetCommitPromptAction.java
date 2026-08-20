package it.consciousdreams;

import com.intellij.ide.plugins.IdeaPluginDescriptor;
import com.intellij.ide.plugins.PluginManagerCore;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.extensions.PluginId;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.util.JDOMUtil;
import com.intellij.openapi.vfs.VirtualFile;
import org.jdom.Element;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.nio.charset.StandardCharsets;

/**
 * Reads and writes the AI commit prompt directly in workspace.xml.
 *
 * AIAssistantCustomInstructionsStorage is annotated with:
 *   @State(name="AIAssistantCustomInstructionsStorage", storages=[@Storage("$WORKSPACE_FILE$")])
 *
 * So all custom instructions are persisted in .idea/workspace.xml under:
 *   <component name="AIAssistantCustomInstructionsStorage">
 *     <option name="instructions">
 *       <map>
 *         <entry key="AIAssistant.VCS.GenerateCommitMessage">
 *           <value>
 *             <AIAssistantStoredInstruction>
 *               <option name="actionId" value="AIAssistant.VCS.GenerateCommitMessage"/>
 *               <option name="content" value="...prompt text..."/>
 *             </AIAssistantStoredInstruction>
 *           </value>
 *         </entry>
 *       </map>
 *     </option>
 *   </component>
 *
 * Writing via IntelliJ's VFS inside a WriteAction fires VirtualFileListener events,
 * which causes StorageVirtualFileTracker to reload the component immediately.
 */
public class SetCommitPromptAction extends AnAction {

    private static final String PLUGIN_ID = "com.intellij.ml.llm";
    private static final String PROMPT_ID = "AIAssistant.VCS.GenerateCommitMessage";
    private static final String STORAGE_COMPONENT = "AIAssistantCustomInstructionsStorage";
    private static final String PROMPT_CLASS = "com.intellij.ml.llm.vcs.LLMCommitCustomizablePrompt";

    @Override
    public void actionPerformed(@NotNull AnActionEvent e) {
        Project project = e.getProject();
        if (project == null) return;

        if (!isAiAssistantInstalled()) {
            Messages.showErrorDialog(project, "AI Assistant plugin is not installed or not active.", "Set Commit Prompt");
            return;
        }

        VirtualFile workspaceFile = project.getWorkspaceFile();
        if (workspaceFile == null) {
            Messages.showErrorDialog(project, "Cannot locate workspace.xml for this project.", "Set Commit Prompt");
            return;
        }

        try {
            String currentPrompt = readPromptFromWorkspace(workspaceFile);

            String newPrompt = Messages.showMultilineInputDialog(
                    project,
                    "Edit the commit message generation prompt:",
                    "Set Commit Prompt",
                    currentPrompt,
                    null,
                    null
            );

            if (newPrompt == null) return;

            writePromptToWorkspace(workspaceFile, newPrompt);
            Messages.showInfoMessage(project, "Commit message prompt updated successfully.", "Set Commit Prompt");

        } catch (Exception ex) {
            Messages.showErrorDialog(project,
                    "Failed to update prompt.\n\n" + ex.getClass().getSimpleName() + ": " + ex.getMessage(),
                    "Set Commit Prompt");
        }
    }

    @Override
    public void update(@NotNull AnActionEvent e) {
        e.getPresentation().setEnabledAndVisible(e.getProject() != null);
    }

    private boolean isAiAssistantInstalled() {
        IdeaPluginDescriptor plugin = PluginManagerCore.getPlugin(PluginId.getId(PLUGIN_ID));
        return plugin != null && plugin.getPluginClassLoader() != null;
    }

    // -------------------------------------------------------------------------
    // Read
    // -------------------------------------------------------------------------

    private String readPromptFromWorkspace(VirtualFile workspaceFile) throws Exception {
        Element root = JDOMUtil.load(workspaceFile.contentsToByteArray());
        Element component = findStorageComponent(root);

        if (component != null) {
            String stored = extractContentValue(component);
            if (stored != null) return stored;
        }

        // No custom instruction stored — return the built-in default (via reflection).
        return readDefaultPrompt();
    }

    @Nullable
    private Element findStorageComponent(Element root) {
        for (Element child : root.getChildren("component")) {
            if (STORAGE_COMPONENT.equals(child.getAttributeValue("name"))) return child;
        }
        return null;
    }

    @Nullable
    private String extractContentValue(Element storageComponent) {
        // <option name="instructions"><map><entry key="..."><value><AIAssistantStoredInstruction>
        //   <option name="content" value="..."/>
        Element instructions = findOption(storageComponent, "instructions");
        if (instructions == null) return null;
        Element map = instructions.getChild("map");
        if (map == null) return null;
        for (Element entry : map.getChildren("entry")) {
            if (PROMPT_ID.equals(entry.getAttributeValue("key"))) {
                Element value = entry.getChild("value");
                if (value == null) continue;
                Element storedInstruction = value.getChild("AIAssistantStoredInstruction");
                if (storedInstruction == null) continue;
                Element contentOption = findOption(storedInstruction, "content");
                if (contentOption != null) return contentOption.getAttributeValue("value");
            }
        }
        return null;
    }

    @Nullable
    private Element findOption(Element parent, String name) {
        for (Element child : parent.getChildren("option")) {
            if (name.equals(child.getAttributeValue("name"))) return child;
        }
        return null;
    }

    // -------------------------------------------------------------------------
    // Write
    // -------------------------------------------------------------------------

    private void writePromptToWorkspace(VirtualFile workspaceFile, String prompt) throws Exception {
        Element root = JDOMUtil.load(workspaceFile.contentsToByteArray());

        Element component = findStorageComponent(root);
        if (component == null) {
            component = new Element("component");
            component.setAttribute("name", STORAGE_COMPONENT);
            root.addContent(component);
        }

        ensureEntryExists(component, prompt);

        // Prepend XML declaration that IntelliJ expects
        String xml = "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n" + JDOMUtil.write(root);
        byte[] bytes = xml.getBytes(StandardCharsets.UTF_8);
        ApplicationManager.getApplication().runWriteAction(() -> {
            try {
                workspaceFile.setBinaryContent(bytes);
            } catch (Exception ex) {
                throw new RuntimeException(ex);
            }
        });
    }

    private void ensureEntryExists(Element storageComponent, String promptText) {
        Element instructionsOption = findOption(storageComponent, "instructions");
        if (instructionsOption == null) {
            instructionsOption = new Element("option");
            instructionsOption.setAttribute("name", "instructions");
            storageComponent.addContent(instructionsOption);
        }

        Element map = instructionsOption.getChild("map");
        if (map == null) {
            map = new Element("map");
            instructionsOption.addContent(map);
        }

        // Find or create entry for PROMPT_ID
        Element entry = null;
        for (Element e : map.getChildren("entry")) {
            if (PROMPT_ID.equals(e.getAttributeValue("key"))) {
                entry = e;
                break;
            }
        }
        if (entry == null) {
            entry = new Element("entry");
            entry.setAttribute("key", PROMPT_ID);
            map.addContent(entry);
        }

        Element value = entry.getChild("value");
        if (value == null) {
            value = new Element("value");
            entry.addContent(value);
        }

        Element storedInstruction = value.getChild("AIAssistantStoredInstruction");
        if (storedInstruction == null) {
            storedInstruction = new Element("AIAssistantStoredInstruction");
            value.addContent(storedInstruction);
        }

        // Ensure actionId option
        Element actionIdOption = findOption(storedInstruction, "actionId");
        if (actionIdOption == null) {
            actionIdOption = new Element("option");
            actionIdOption.setAttribute("name", "actionId");
            storedInstruction.addContent(actionIdOption);
        }
        actionIdOption.setAttribute("value", PROMPT_ID);

        // Update content option
        Element contentOption = findOption(storedInstruction, "content");
        if (contentOption == null) {
            contentOption = new Element("option");
            contentOption.setAttribute("name", "content");
            storedInstruction.addContent(contentOption);
        }
        contentOption.setAttribute("value", promptText);
    }

    // -------------------------------------------------------------------------
    // Default prompt via reflection (LLMCommitCustomizablePrompt is in ml-llm.jar,
    // accessible from the main plugin classloader).
    // -------------------------------------------------------------------------

    private String readDefaultPrompt() {
        try {
            IdeaPluginDescriptor plugin = PluginManagerCore.getPlugin(PluginId.getId(PLUGIN_ID));
            if (plugin == null) return "";
            ClassLoader cl = plugin.getPluginClassLoader();
            Class<?> cls = cl.loadClass(PROMPT_CLASS);
            Object instance = cls.getDeclaredConstructor().newInstance();
            Object psString = cls.getMethod("getDefaultPrompt").invoke(instance);
            return psString != null ? psString.toString() : "";
        } catch (Exception e) {
            return "";
        }
    }
}