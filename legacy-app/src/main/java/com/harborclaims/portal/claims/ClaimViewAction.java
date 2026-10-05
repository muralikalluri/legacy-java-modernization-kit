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
        java.util.List<ClaimNote> notes = ClaimManager.getInstance().notes(id);
        com.harborclaims.portal.policies.Policy policy =
                com.harborclaims.portal.policies.PolicyManager.getInstance().get(claim.getPolicyId());
        request.setAttribute("claim", claim);
        request.setAttribute("notes", notes);
        request.setAttribute("insight", ClaimManager.insightFor(claim, policy,
                ClaimManager.getInstance().listByPolicy(claim.getPolicyId()), notes, currentUser(request).getRole(),
                ClaimManager.today()));
        request.setAttribute("transitions", ClaimManager.getInstance().availableTransitions(currentUser(request), claim));
        return mapping.findForward("success");
    }
}
