package com.harborclaims.portal.claims;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.apache.struts.action.ActionForm;
import org.apache.struts.action.ActionForward;
import org.apache.struts.action.ActionMapping;

import com.harborclaims.portal.common.BaseAction;

public class ClaimNoteAction extends BaseAction {

    @Override
    protected ActionForward doExecute(ActionMapping mapping, ActionForm form, HttpServletRequest request,
            HttpServletResponse response) throws Exception {
        NoteForm f = (NoteForm) form;
        int id = Integer.parseInt(f.getId().trim());
        ClaimManager.getInstance().addNote(id, f.getNote(), currentUser(request));
        return new ActionForward("/claim/view.do?id=" + id, true);
    }
}
