package com.harborclaims.portal.claims;

import javax.servlet.http.HttpServletRequest;

import org.apache.struts.action.ActionErrors;
import org.apache.struts.action.ActionForm;
import org.apache.struts.action.ActionMapping;
import org.apache.struts.action.ActionMessage;

public class NoteForm extends ActionForm {

    private static final long serialVersionUID = 1L;

    private String id = "";
    private String note = "";

    @Override
    public ActionErrors validate(ActionMapping mapping, HttpServletRequest request) {
        ActionErrors errors = new ActionErrors();
        if (id == null || !id.trim().matches("[0-9]{1,9}")) {
            errors.add("id", new ActionMessage("error.id.invalid"));
        }
        if (note == null || note.trim().length() == 0) {
            errors.add("note", new ActionMessage("error.required", "note"));
        } else if (note.trim().length() > 1000) {
            errors.add("note", new ActionMessage("error.too.long", "note", 1000));
        }
        return errors;
    }

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getNote() {
        return note;
    }

    public void setNote(String note) {
        this.note = note;
    }
}
