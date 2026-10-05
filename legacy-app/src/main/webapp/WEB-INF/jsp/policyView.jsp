<%@ page contentType="text/html;charset=UTF-8" %>
<%@ page import="com.harborclaims.portal.auth.AppUser" %>
<%@ taglib uri="http://struts.apache.org/tags-html" prefix="html" %>
<%@ taglib uri="http://struts.apache.org/tags-bean" prefix="bean" %>
<%@ taglib uri="http://struts.apache.org/tags-logic" prefix="logic" %>
<%
    AppUser who = (AppUser) session.getAttribute("currentUser");
    String myRole = who.getRole();
    boolean canEdit = "ADMIN".equals(myRole) || "UNDERWRITER".equals(myRole);
    boolean canCancel = canEdit || "SUPERVISOR".equals(myRole);
%>
<h2 id="policy-number"><bean:write name="policy" property="policyNumber"/></h2>
<logic:messagesPresent>
  <div id="errors"><html:errors/></div>
</logic:messagesPresent>
<table id="policy-details" data-policy-id="<bean:write name="policy" property="id"/>" data-status="<bean:write name="policy" property="status"/>">
  <tr><th>Customer</th><td><a href="${pageContext.request.contextPath}/customers/view.do?id=<bean:write name="policy" property="customerId"/>"><bean:write name="policy" property="customerName"/></a></td></tr>
  <tr><th>Product</th><td><bean:write name="policy" property="productType"/></td></tr>
  <tr><th>Coverage limit</th><td><bean:write name="policy" property="coverageLimitDisplay"/></td></tr>
  <tr><th>Premium</th><td><bean:write name="policy" property="premiumDisplay"/></td></tr>
  <tr><th>Period</th><td id="policy-period"><bean:write name="policy" property="startDate"/> to <bean:write name="policy" property="endDate"/></td></tr>
  <tr><th>Status</th><td id="policy-status"><bean:write name="policy" property="status"/></td></tr>
  <tr><th>Cancelled on</th><td><bean:write name="policy" property="cancelledOn" ignore="true"/></td></tr>
</table>
<h3>Claims on this policy</h3>
<p id="claims-summary"><bean:write name="summary" property="text"/>. Reserve held: <bean:write name="summary" property="reserveDisplay"/></p>
<p id="claims-by-status"><bean:write name="summary" property="byStatus"/>. Largest claim: <bean:write name="summary" property="largestDisplay"/></p>
<table id="policy-claims">
  <tr><th>Claim</th><th>Loss date</th><th>Claimed</th><th>Status</th></tr>
  <logic:iterate id="cl" name="claims">
    <tr data-claim-id="<bean:write name="cl" property="id"/>" data-status="<bean:write name="cl" property="status"/>">
      <td><a href="${pageContext.request.contextPath}/claim/view.do?id=<bean:write name="cl" property="id"/>"><bean:write name="cl" property="claimNumber"/></a></td>
      <td><bean:write name="cl" property="lossDate"/></td>
      <td><bean:write name="cl" property="amountClaimedDisplay"/></td>
      <td><bean:write name="cl" property="status"/></td>
    </tr>
  </logic:iterate>
</table>
<% if (canEdit) { %>
  <p><a id="edit-link" href="${pageContext.request.contextPath}/policy/edit.do?id=<bean:write name="policy" property="id"/>">Edit terms</a></p>
  <html:form action="/policy/renew" method="post">
    <input type="hidden" name="id" value="<bean:write name="policy" property="id"/>"/>
    <input type="hidden" name="expectedEnd" value="<bean:write name="policy" property="endDate"/>"/>
    <html:submit value="Renew for one year"/>
  </html:form>
<% } %>
<% if (canCancel) { %>
  <html:form action="/policy/cancel" method="post">
    <input type="hidden" name="id" value="<bean:write name="policy" property="id"/>"/>
    <html:submit value="Cancel policy"/>
  </html:form>
<% } %>
<p><a href="${pageContext.request.contextPath}/policies/list.do">Back to policies</a></p>
