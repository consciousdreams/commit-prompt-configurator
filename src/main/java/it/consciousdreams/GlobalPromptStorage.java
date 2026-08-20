package it.consciousdreams;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.PathManager;
import com.intellij.openapi.components.Service;
import org.jetbrains.annotations.NotNull;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

// Stored outside the versioned JetBrains config dir so the prompt survives IDE upgrades.
// Path: <JetBrains-root>/commit-prompt-configurator/prompt.txt
// e.g. ~/Library/Application Support/JetBrains/commit-prompt-configurator/prompt.txt
@Service(Service.Level.APP)
public final class GlobalPromptStorage {

    private static final String DIR_NAME = "commit-prompt-configurator";
    private static final String FILE_NAME = "prompt.txt";

    private String prompt = "";

    public GlobalPromptStorage() {
        load();
    }

    public static GlobalPromptStorage getInstance() {
        return ApplicationManager.getApplication().getService(GlobalPromptStorage.class);
    }

    public String getPrompt() {
        load();
        return prompt;
    }

    public void setPrompt(@NotNull String newPrompt) {
        prompt = newPrompt;
        save();
    }

    private Path storagePath() {
        // intentionally local: prompt must persist on this machine across IDE upgrades
        return PathManager.getConfigDir().getParent()
                .resolve(DIR_NAME)
                .resolve(FILE_NAME);
    }

    private void load() {
        Path file = storagePath();
        if (Files.exists(file)) {
            try {
                prompt = Files.readString(file, StandardCharsets.UTF_8);
            } catch (IOException e) {
                // best-effort: if unreadable, start with empty prompt
            }
        }
    }

    private void save() {
        Path file = storagePath();
        try {
            Files.createDirectories(file.getParent());
            Files.writeString(file, prompt, StandardCharsets.UTF_8);
        } catch (IOException e) {
            // best-effort: if unwritable, the prompt is still in memory for this session
        }
    }
}