package it.consciousdreams;

import com.intellij.ide.plugins.IdeaPluginDescriptor;
import com.intellij.ide.plugins.PluginManager;
import com.intellij.openapi.actionSystem.ActionUpdateThread;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.extensions.PluginId;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.vfs.VirtualFile;
import org.jdom.JDOMException;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;

import static java.util.Objects.requireNonNull;

public class SetCommitPromptAction extends AnAction {

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

        boolean aiAssistantActive = isAiAssistantActive();
        boolean airActive = isAirActive();
        if (!aiAssistantActive && !airActive) {
            Messages.showErrorDialog(project, "AI Assistant or Air must be active to edit the commit message prompt.", DIALOG_TITLE);
            return;
        }

        VirtualFile workspaceFile = aiAssistantActive ? project.getWorkspaceFile() : null;
        if (aiAssistantActive && workspaceFile == null) {
            Messages.showErrorDialog(project, "Cannot locate workspace.xml for this project.", DIALOG_TITLE);
            return;
        }

        try {
            String airDefaultPrompt = airActive ? AirPrompt.defaultPrompt() : "";
            String defaultPrompt = airActive ? airDefaultPrompt : readDefaultPrompt();
            String currentPrompt = readCurrentPrompt(project, workspaceFile, defaultPrompt, airActive, aiAssistantActive);

            CommitPromptDialog dialog = new CommitPromptDialog(currentPrompt, defaultPrompt);
            if (!dialog.showAndGet()) return;

            String newPrompt = dialog.getPrompt();
            if (aiAssistantActive) AiAssistantPrompt.write(workspaceFile, newPrompt);
            if (airActive) AirPrompt.write(project, newPrompt, airDefaultPrompt);
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
        e.getPresentation().setVisible(e.getProject() != null);
        e.getPresentation().setEnabled(e.getProject() != null && (isAiAssistantActive() || isAirActive()));
    }

    private boolean isAiAssistantActive() {
        try {
            Class.forName(PROMPT_CLASS);
            return true;
        } catch (ClassNotFoundException e) {
            return false;
        }
    }

    private boolean isAirActive() {
        IdeaPluginDescriptor plugin = PluginManager.getInstance()
                .findEnabledPlugin(PluginId.getId(AirPrompt.AIR_PLUGIN_ID));
        return plugin != null;
    }

    private String readCurrentPrompt(Project project, @Nullable VirtualFile workspaceFile, String defaultPrompt,
                                     boolean airActive, boolean aiAssistantActive) throws IOException, JDOMException {
        String fromGlobal = GlobalPromptStorage.getInstance().getPrompt();
        if (!fromGlobal.isEmpty()) return fromGlobal;

        if (airActive) {
            String fromAir = AirPrompt.read(project, defaultPrompt);
            if (fromAir != null) return fromAir;
        }

        if (aiAssistantActive) {
            String fromWorkspace = AiAssistantPrompt.read(requireNonNull(workspaceFile));
            if (fromWorkspace != null) return fromWorkspace;
        }

        return defaultPrompt;
    }

    // LLMCommitCustomizablePrompt lives in ml-llm.jar, visible from the plugin classloader.
    private String readDefaultPrompt() {
        try {
            Class<?> cls = Class.forName(PROMPT_CLASS);
            Object instance = cls.getDeclaredConstructor().newInstance();
            Object result = cls.getMethod("getDefaultPrompt").invoke(instance);
            return result != null ? result.toString() : "";
        } catch (ReflectiveOperationException e) {
            return "";
        }
    }
}
