<%@ page contentType="text/html;charset=UTF-8" %>
<%@ taglib uri="http://struts.apache.org/tags-html" prefix="html" %>
<%@ taglib uri="http://struts.apache.org/tags-bean" prefix="bean" %>
<%@ taglib uri="http://struts.apache.org/tags-logic" prefix="logic" %>
<h2>Claims</h2>
<html:form action="/claims/search" method="get">
  Search <html:text property="q"/>
  Status
  <html:select property="status">
    <html:option value="">Any</html:option>
    <html:options name="statuses"/>
  </html:select>
  <html:submit value="Search"/>
</html:form>
<p>Total: <span id="total"><bean:write name="result" property="total"/></span></p>
<table id="results">
  <tr><th>Claim</th><th>Customer</th><th>Policy</th><th>Loss date</th><th>Claimed</th><th>Status</th></tr>
  <logic:iterate id="cl" name="result" property="items">
    <tr data-claim-id="<bean:write name="cl" property="id"/>" data-status="<bean:write name="cl" property="status"/>">
      <td><a href="${pageContext.request.contextPath}/claim/view.do?id=<bean:write name="cl" property="id"/>"><bean:write name="cl" property="claimNumber"/></a></td>
      <td><bean:write name="cl" property="customerName"/></td>
      <td><bean:write name="cl" property="policyNumber"/></td>
      <td><bean:write name="cl" property="lossDate"/></td>
      <td><bean:write name="cl" property="amountClaimedDisplay"/></td>
      <td><bean:write name="cl" property="status"/></td>
    </tr>
  </logic:iterate>
</table>
<jsp:include page="/WEB-INF/jsp/pager.jsp"/>
<p><a href="${pageContext.request.contextPath}/claim/new.do">File a new claim</a></p>
