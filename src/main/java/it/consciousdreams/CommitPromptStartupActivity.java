package it.consciousdreams;

import com.intellij.openapi.project.DumbAware;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.startup.StartupActivity;
import com.intellij.openapi.vfs.VirtualFile;
import org.jetbrains.annotations.NotNull;

public class CommitPromptStartupActivity implements StartupActivity, DumbAware {

    @Override
    public void runActivity(@NotNull Project project) {
        String globalPrompt = GlobalPromptStorage.getInstance().getPrompt();
        if (globalPrompt.isEmpty()) return;

        VirtualFile workspaceFile = project.getWorkspaceFile();
        if (workspaceFile == null) return;

        try {
            WorkspacePrompt.write(workspaceFile, globalPrompt);
        } catch (Exception ignored) {
        }
    }
}