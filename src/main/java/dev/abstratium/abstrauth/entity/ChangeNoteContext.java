package dev.abstratium.abstrauth.entity;

import jakarta.enterprise.context.RequestScoped;

@RequestScoped
public class ChangeNoteContext {

    private String changeNote;

    public String getChangeNote() {
        return changeNote;
    }

    public void setChangeNote(String changeNote) {
        this.changeNote = changeNote;
    }

    public boolean hasChangeNote() {
        return changeNote != null && !changeNote.isBlank();
    }
}
