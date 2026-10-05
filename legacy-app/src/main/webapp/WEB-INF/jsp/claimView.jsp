<%@ page contentType="text/html;charset=UTF-8" %>
<%@ page import="com.harborclaims.portal.claims.Claim" %>
<%@ taglib uri="http://struts.apache.org/tags-html" prefix="html" %>
<%@ taglib uri="http://struts.apache.org/tags-bean" prefix="bean" %>
<%@ taglib uri="http://struts.apache.org/tags-logic" prefix="logic" %>
<% Claim shown = (Claim) request.getAttribute("claim"); %>
<h2 id="claim-number"><bean:write name="claim" property="claimNumber"/></h2>
<logic:messagesPresent>
  <div id="errors"><html:errors/></div>
</logic:messagesPresent>
<table id="claim-details" data-claim-id="<bean:write name="claim" property="id"/>" data-status="<bean:write name="claim" property="status"/>">
  <tr><th>Status</th><td><span class="badge <%= com.harborclaims.portal.claims.ClaimManager.statusCss(shown.getStatus()) %>" id="claim-status"><bean:write name="claim" property="status"/></span></td></tr>
  <tr><th>Customer</th><td><a href="${pageContext.request.contextPath}/customers/view.do?id=<bean:write name="claim" property="customerId"/>"><bean:write name="claim" property="customerName"/></a></td></tr>
  <tr><th>Policy</th><td><bean:write name="claim" property="policyNumber"/></td></tr>
  <tr><th>Loss date</th><td><bean:write name="claim" property="lossDate"/></td></tr>
  <tr><th>Reported</th><td><bean:write name="claim" property="reportedDate"/></td></tr>
  <tr><th>Claimed</th><td id="amount-claimed"><bean:write name="claim" property="amountClaimedDisplay"/></td></tr>
  <tr><th>Approved</th><td id="amount-approved"><bean:write name="claim" property="amountApprovedDisplay"/></td></tr>
  <tr><th>Description</th><td><bean:write name="claim" property="description"/></td></tr>
</table>

<div id="insight">
  <p id="next-action"><strong>Next:</strong> <bean:write name="insight" property="hint"/></p>
  <p id="sla" data-sla="<bean:write name="insight" property="slaState"/>"><bean:write name="insight" property="sla"/></p>
  <p id="reserve">Reserve held: <bean:write name="insight" property="reserve"/></p>
  <logic:notEmpty name="insight" property="flags">
    <ul id="flags">
      <logic:iterate id="f" name="insight" property="flags">
        <li><bean:write name="f"/></li>
      </logic:iterate>
    </ul>
  </logic:notEmpty>
</div>

<logic:notEmpty name="transitions">
  <h3>Change status</h3>
  <html:form action="/claim/transition" method="post">
    <input type="hidden" name="id" value="<bean:write name="claim" property="id"/>"/>
    <label>New status
      <select name="toStatus" id="toStatus">
        <logic:iterate id="t" name="transitions">
          <option value="<bean:write name="t"/>"><bean:write name="t"/></option>
        </logic:iterate>
      </select>
    </label>
    <label>Approved amount (blank means claimed amount) <input type="text" name="approvedAmount"/></label>
    <label>Reason <input type="text" name="reason" size="60"/></label>
    <p><html:submit value="Apply"/></p>
  </html:form>
</logic:notEmpty>

<logic:notEmpty name="insight" property="letter">
  <h3>Customer letter preview</h3>
  <pre id="letter"><bean:write name="insight" property="letter"/></pre>
</logic:notEmpty>

<h3>Notes</h3>
<ul id="notes">
  <logic:iterate id="n" name="notes">
    <li><bean:write name="n" property="createdAt"/> <strong><bean:write name="n" property="author"/></strong>: <bean:write name="n" property="note"/></li>
  </logic:iterate>
</ul>
<html:form action="/claim/note" method="post">
  <input type="hidden" name="id" value="<bean:write name="claim" property="id"/>"/>
  <label>Add note <input type="text" name="note" size="60"/></label>
  <p><html:submit value="Add note"/></p>
</html:form>
<p><a href="${pageContext.request.contextPath}/claims/search.do">Back to claims</a></p>
