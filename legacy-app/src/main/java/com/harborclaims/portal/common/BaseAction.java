package com.harborclaims.portal.common;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.apache.struts.action.Action;
import org.apache.struts.action.ActionErrors;
import org.apache.struts.action.ActionForm;
import org.apache.struts.action.ActionForward;
import org.apache.struts.action.ActionMapping;
import org.apache.struts.action.ActionMessage;

import com.harborclaims.portal.auth.AppUser;
import com.harborclaims.portal.auth.LoginFilter;

/** Shared plumbing: current user, 404 mapping, business errors to ActionErrors. */
public abstract class BaseAction extends Action {

    @Override
    public final ActionForward execute(ActionMapping mapping, ActionForm form, HttpServletRequest request,
            HttpServletResponse response) throws Exception {
        try {
            return doExecute(mapping, form, request, response);
        } catch (NotFoundException e) {
            response.sendError(HttpServletResponse.SC_NOT_FOUND, e.getMessage());
            return null;
        }
    }

    protected abstract ActionForward doExecute(ActionMapping mapping, ActionForm form, HttpServletRequest request,
            HttpServletResponse response) throws Exception;

    protected AppUser currentUser(HttpServletRequest request) {
        return (AppUser) request.getSession().getAttribute(LoginFilter.SESSION_USER);
    }

    protected ActionErrors errorsFor(BusinessException e) {
        ActionErrors errors = new ActionErrors();
        errors.add(ActionErrors.GLOBAL_MESSAGE, new ActionMessage(e.getKey(), e.getArgs()));
        return errors;
    }

    protected int intParam(HttpServletRequest request, String name) throws NotFoundException {
        try {
            return Integer.parseInt(request.getParameter(name).trim());
        } catch (RuntimeException e) {
            throw new NotFoundException("Unknown " + name);
        }
    }
}
