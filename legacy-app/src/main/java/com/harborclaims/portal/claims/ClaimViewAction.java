package com.harborclaims.portal.claims;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.apache.struts.action.ActionForm;
import org.apache.struts.action.ActionForward;
import org.apache.struts.action.ActionMapping;

import com.harborclaims.portal.common.BaseAction;

public class ClaimViewAction extends BaseAction {

    @Override
    protected ActionForward doExecute(ActionMapping mapping, ActionForm form, HttpServletRequest request,
            HttpServletResponse response) throws Exception {
        int id = intParam(request, "id");
        Claim claim = ClaimManager.getInstance().get(id);
        request.setAttribute("claim", claim);
        request.setAttribute("notes", ClaimManager.getInstance().notes(id));
        request.setAttribute("transitions", ClaimManager.getInstance().availableTransitions(currentUser(request), claim));
        return mapping.findForward("success");
    }
}
