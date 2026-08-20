package it.consciousdreams;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.components.PersistentStateComponent;
import com.intellij.openapi.components.Service;
import com.intellij.openapi.components.State;
import com.intellij.openapi.components.Storage;
import org.jetbrains.annotations.NotNull;

@Service(Service.Level.APP)
@State(name = "CommitPromptConfigurator", storages = @Storage("commit-prompt-configurator.xml"))
public class GlobalPromptStorage implements PersistentStateComponent<GlobalPromptStorage.State> {

    public static class State {
        public String prompt = "";
    }

    private State myState = new State();

    public static GlobalPromptStorage getInstance() {
        return ApplicationManager.getApplication().getService(GlobalPromptStorage.class);
    }

    @Override
    public @NotNull State getState() {
        return myState;
    }

    @Override
    public void loadState(@NotNull State state) {
        myState = state;
    }

    public String getPrompt() {
        return myState.prompt != null ? myState.prompt : "";
    }

    public void setPrompt(@NotNull String prompt) {
        myState.prompt = prompt;
    }
}