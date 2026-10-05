<%@ page contentType="text/html;charset=UTF-8" %>
<%@ taglib uri="http://struts.apache.org/tags-html" prefix="html" %>
<%@ taglib uri="http://struts.apache.org/tags-bean" prefix="bean" %>
<%@ taglib uri="http://struts.apache.org/tags-logic" prefix="logic" %>
<h2>Policies</h2>
<html:form action="/policies/search" method="get">
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
  <tr><th>Policy</th><th>Customer</th><th>Product</th><th>Coverage limit</th><th>Ends</th><th>Status</th></tr>
  <logic:iterate id="p" name="result" property="items">
    <tr data-policy-id="<bean:write name="p" property="id"/>" data-status="<bean:write name="p" property="status"/>">
      <td><a href="${pageContext.request.contextPath}/policy/view.do?id=<bean:write name="p" property="id"/>"><bean:write name="p" property="policyNumber"/></a></td>
      <td><bean:write name="p" property="customerName"/></td>
      <td><bean:write name="p" property="productType"/></td>
      <td><bean:write name="p" property="coverageLimitDisplay"/></td>
      <td><bean:write name="p" property="endDate"/></td>
      <td><bean:write name="p" property="status"/></td>
    </tr>
  </logic:iterate>
</table>
<jsp:include page="/WEB-INF/jsp/pager.jsp"/>
<p><a href="${pageContext.request.contextPath}/policy/edit.do">New policy</a></p>
