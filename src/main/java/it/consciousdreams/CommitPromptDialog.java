package it.consciousdreams;

import com.intellij.openapi.ui.DialogWrapper;
import com.intellij.ui.components.JBScrollPane;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;

public class CommitPromptDialog extends DialogWrapper {

    private final String defaultPrompt;
    private JTextArea textArea;

    public CommitPromptDialog(@NotNull String currentPrompt, @NotNull String defaultPrompt) {
        super(true);
        this.defaultPrompt = defaultPrompt;
        setTitle("Set Commit Prompt");
        setOKButtonText("Save");
        init();
        textArea.setText(currentPrompt);
        textArea.setCaretPosition(0);
    }

    @Override
    protected @Nullable JComponent createCenterPanel() {
        textArea = new JTextArea(20, 80);
        textArea.setLineWrap(true);
        textArea.setWrapStyleWord(true);
        textArea.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 13));
        return new JBScrollPane(textArea);
    }

    @Override
    protected Action @NotNull [] createLeftSideActions() {
        return new Action[]{new DialogWrapperAction("Reset to Default") {
            @Override
            protected void doAction(java.awt.event.ActionEvent e) {
                textArea.setText(defaultPrompt);
                textArea.setCaretPosition(0);
            }
        }};
    }

    public String getPrompt() {
        return textArea.getText();
    }
}