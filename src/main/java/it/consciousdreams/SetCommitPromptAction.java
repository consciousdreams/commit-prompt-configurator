package it.consciousdreams;

import com.intellij.openapi.actionSystem.ActionUpdateThread;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.vfs.VirtualFile;
import org.jdom.JDOMException;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;

import static java.util.Objects.requireNonNull;

public class SetCommitPromptAction extends AnAction {

    private static final String DIALOG_TITLE = "Set Commit Prompt";

    @Override
    public @NotNull ActionUpdateThread getActionUpdateThread() {
        return ActionUpdateThread.BGT;
    }

    @Override
    public void actionPerformed(@NotNull AnActionEvent e) {
        Project project = e.getProject();
        if (project == null) return;

        boolean aiAssistantActive = AiAssistantPrompt.isActive();
        boolean airActive = AirPrompt.isActive();
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
            String defaultPrompt = airActive ? airDefaultPrompt : AiAssistantPrompt.defaultPrompt();
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
        e.getPresentation().setEnabled(e.getProject() != null && (AiAssistantPrompt.isActive() || AirPrompt.isActive()));
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
}
