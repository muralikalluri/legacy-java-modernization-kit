<%@ page contentType="text/html;charset=UTF-8" %>
<%@ taglib uri="http://struts.apache.org/tags-html" prefix="html" %>
<%@ taglib uri="http://struts.apache.org/tags-bean" prefix="bean" %>
<%@ taglib uri="http://struts.apache.org/tags-logic" prefix="logic" %>
<h2>Users</h2>
<logic:messagesPresent>
  <div id="errors"><html:errors/></div>
</logic:messagesPresent>
<html:form action="/admin/users" method="get">
  Search <html:text property="q"/> <html:submit value="Search"/>
</html:form>
<p>Total: <span id="total"><bean:write name="result" property="total"/></span></p>
<table id="results">
  <tr><th>Username</th><th>Name</th><th>Role</th><th>Active</th><th></th></tr>
  <logic:iterate id="u" name="result" property="items">
    <tr data-user-id="<bean:write name="u" property="id"/>">
      <td><a href="${pageContext.request.contextPath}/admin/userEdit.do?id=<bean:write name="u" property="id"/>"><bean:write name="u" property="username"/></a></td>
      <td><bean:write name="u" property="displayName"/></td>
      <td><bean:write name="u" property="role"/></td>
      <td><bean:write name="u" property="active"/></td>
      <td><a href="${pageContext.request.contextPath}/admin/userToggle.do?id=<bean:write name="u" property="id"/>">Toggle active</a></td>
    </tr>
  </logic:iterate>
</table>
<jsp:include page="/WEB-INF/jsp/pager.jsp"/>
<p><a href="${pageContext.request.contextPath}/admin/userEdit.do">New user</a></p>
