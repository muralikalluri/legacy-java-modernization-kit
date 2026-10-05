<%@ page contentType="text/html;charset=UTF-8" %>
<%@ page import="com.harborclaims.portal.auth.AppUser" %>
<%@ taglib uri="http://struts.apache.org/tags-html" prefix="html" %>
<%@ taglib uri="http://struts.apache.org/tags-bean" prefix="bean" %>
<%@ taglib uri="http://struts.apache.org/tags-logic" prefix="logic" %>
<%
    AppUser viewer = (AppUser) session.getAttribute("currentUser");
    String role = viewer.getRole();
%>
<h2 id="customer-name"><bean:write name="customer" property="fullName"/></h2>
<logic:messagesPresent>
  <div id="errors"><html:errors/></div>
</logic:messagesPresent>
<table id="customer-details" data-customer-id="<bean:write name="customer" property="id"/>">
  <tr><th>Email</th><td><bean:write name="customer" property="email"/></td></tr>
  <tr><th>Phone</th><td><bean:write name="customer" property="phone" ignore="true"/></td></tr>
  <tr><th>Date of birth</th><td><bean:write name="customer" property="dateOfBirth" ignore="true"/></td></tr>
  <tr><th>Address</th><td><bean:write name="customer" property="addressLine" ignore="true"/>, <bean:write name="customer" property="city" ignore="true"/> <bean:write name="customer" property="state" ignore="true"/> <bean:write name="customer" property="postalCode" ignore="true"/></td></tr>
</table>
<h3>Policies</h3>
<table id="policies">
  <tr><th>Policy</th><th>Product</th><th>Coverage limit</th><th>Period</th><th>Status</th></tr>
  <logic:iterate id="p" name="policies">
    <tr>
      <td><bean:write name="p" property="policyNumber"/></td>
      <td><bean:write name="p" property="productType"/></td>
      <td><bean:write name="p" property="coverageLimitDisplay"/></td>
      <td><bean:write name="p" property="startDate"/> to <bean:write name="p" property="endDate"/></td>
      <td><bean:write name="p" property="status"/></td>
    </tr>
  </logic:iterate>
</table>
<p>
  <% if ("ADMIN".equals(role) || "SUPERVISOR".equals(role) || "CSR".equals(role)) { %>
    <a id="edit-link" href="${pageContext.request.contextPath}/customers/edit.do?id=<bean:write name="customer" property="id"/>">Edit</a>
  <% } %>
  <% if ("ADMIN".equals(role)) { %>
    <a id="delete-link" href="${pageContext.request.contextPath}/customers/delete.do?id=<bean:write name="customer" property="id"/>">Delete</a>
  <% } %>
  <a href="${pageContext.request.contextPath}/customers/list.do">Back to list</a>
</p>
