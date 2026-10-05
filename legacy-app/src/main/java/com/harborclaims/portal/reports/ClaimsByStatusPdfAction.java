package com.harborclaims.portal.reports;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.apache.struts.action.ActionForm;
import org.apache.struts.action.ActionForward;
import org.apache.struts.action.ActionMapping;

import com.harborclaims.portal.common.BaseAction;

/** Builds the whole PDF in memory first, so a failure never sends a half-written file with a 200. */
public class ClaimsByStatusPdfAction extends BaseAction {

    @Override
    protected ActionForward doExecute(ActionMapping mapping, ActionForm form, HttpServletRequest request,
            HttpServletResponse response) throws Exception {
        byte[] pdf = ReportManager.getInstance().claimsByStatusPdf();
        response.setContentType("application/pdf");
        response.setHeader("Content-Disposition", "inline; filename=\"claims-by-status.pdf\"");
        response.setContentLength(pdf.length);
        response.getOutputStream().write(pdf);
        response.getOutputStream().flush();
        return null;
    }
}
