package it.consciousdreams;

import com.intellij.ide.plugins.IdeaPluginDescriptor;
import com.intellij.ide.plugins.PluginManagerCore;
import com.intellij.openapi.actionSystem.ActionUpdateThread;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.extensions.PluginId;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.vfs.VirtualFile;
import org.jdom.JDOMException;
import org.jetbrains.annotations.NotNull;

import java.io.IOException;

public class SetCommitPromptAction extends AnAction {

    private static final String PLUGIN_ID = "com.intellij.ml.llm";
    private static final String PROMPT_CLASS = "com.intellij.ml.llm.vcs.LLMCommitCustomizablePrompt";
    private static final String DIALOG_TITLE = "Set Commit Prompt";

    @Override
    public @NotNull ActionUpdateThread getActionUpdateThread() {
        return ActionUpdateThread.BGT;
    }

    @Override
    public void actionPerformed(@NotNull AnActionEvent e) {
        Project project = e.getProject();
        if (project == null) return;

        if (!isAiAssistantInstalled()) {
            Messages.showErrorDialog(project, "AI Assistant plugin is not installed or not active.", DIALOG_TITLE);
            return;
        }

        VirtualFile workspaceFile = project.getWorkspaceFile();
        if (workspaceFile == null) {
            Messages.showErrorDialog(project, "Cannot locate workspace.xml for this project.", DIALOG_TITLE);
            return;
        }

        try {
            String currentPrompt = readCurrentPrompt(workspaceFile);

            String newPrompt = Messages.showMultilineInputDialog(
                    project,
                    "Edit the commit message generation prompt:",
                    DIALOG_TITLE,
                    currentPrompt,
                    null,
                    null
            );

            if (newPrompt == null) return;

            WorkspacePrompt.write(workspaceFile, newPrompt);
            GlobalPromptStorage.getInstance().setPrompt(newPrompt);
            Messages.showInfoMessage(project, "Commit message prompt updated successfully.", DIALOG_TITLE);

        } catch (IOException | JDOMException ex) {
            Messages.showErrorDialog(project,
                    "Failed to update prompt.\n\n" + ex.getClass().getSimpleName() + ": " + ex.getMessage(),
                    DIALOG_TITLE);
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

    private String readCurrentPrompt(VirtualFile workspaceFile) throws IOException, JDOMException {
        String fromGlobal = GlobalPromptStorage.getInstance().getPrompt();
        if (!fromGlobal.isEmpty()) return fromGlobal;

        String fromWorkspace = WorkspacePrompt.read(workspaceFile);
        if (fromWorkspace != null) return fromWorkspace;

        return readDefaultPrompt();
    }

    // LLMCommitCustomizablePrompt lives in ml-llm.jar, visible from the main plugin classloader.
    private String readDefaultPrompt() {
        try {
            IdeaPluginDescriptor plugin = PluginManagerCore.getPlugin(PluginId.getId(PLUGIN_ID));
            if (plugin == null) return "";
            ClassLoader cl = plugin.getPluginClassLoader();
            if (cl == null) return "";
            Class<?> cls = cl.loadClass(PROMPT_CLASS);
            Object instance = cls.getDeclaredConstructor().newInstance();
            Object psString = cls.getMethod("getDefaultPrompt").invoke(instance);
            return psString != null ? psString.toString() : "";
        } catch (ReflectiveOperationException e) {
            return "";
        }
    }
}
