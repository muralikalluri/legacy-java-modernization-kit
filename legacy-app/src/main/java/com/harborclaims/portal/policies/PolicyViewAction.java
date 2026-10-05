package com.harborclaims.portal.policies;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.apache.struts.action.ActionForm;
import org.apache.struts.action.ActionForward;
import org.apache.struts.action.ActionMapping;

import com.harborclaims.portal.common.BaseAction;

public class PolicyViewAction extends BaseAction {

    @Override
    protected ActionForward doExecute(ActionMapping mapping, ActionForm form, HttpServletRequest request,
            HttpServletResponse response) throws Exception {
        int id = intParam(request, "id");
        Policy policy = PolicyManager.getInstance().get(id);
        java.util.List<com.harborclaims.portal.claims.Claim> claims = PolicyManager.getInstance().claimsOf(id);
        request.setAttribute("policy", policy);
        request.setAttribute("claims", claims);
        request.setAttribute("summary", com.harborclaims.portal.claims.ClaimManager.summarize(claims, policy));
        return mapping.findForward("success");
    }
}
