package it.consciousdreams;

import com.intellij.openapi.actionSystem.ActionUpdateThread;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.vfs.VirtualFile;
import org.jdom.JDOMException;
import org.jetbrains.annotations.NotNull;

import java.io.IOException;

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
            String defaultPrompt = readDefaultPrompt();

            CommitPromptDialog dialog = new CommitPromptDialog(currentPrompt, defaultPrompt);
            if (!dialog.showAndGet()) return;

            String newPrompt = dialog.getPrompt();
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
        try {
            Class.forName(PROMPT_CLASS);
            return true;
        } catch (ClassNotFoundException e) {
            return false;
        }
    }

    private String readCurrentPrompt(VirtualFile workspaceFile) throws IOException, JDOMException {
        String fromGlobal = GlobalPromptStorage.getInstance().getPrompt();
        if (!fromGlobal.isEmpty()) return fromGlobal;

        String fromWorkspace = WorkspacePrompt.read(workspaceFile);
        if (fromWorkspace != null) return fromWorkspace;

        return readDefaultPrompt();
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
