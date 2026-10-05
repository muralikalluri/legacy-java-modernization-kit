<%@ page contentType="text/html;charset=UTF-8" %>
<%@ taglib uri="http://struts.apache.org/tags-html" prefix="html" %>
<%@ taglib uri="http://struts.apache.org/tags-bean" prefix="bean" %>
<%@ taglib uri="http://struts.apache.org/tags-logic" prefix="logic" %>
<h2>Customers</h2>
<html:form action="/customers/search" method="get">
  Search <html:text property="q"/> <html:submit value="Search"/>
</html:form>
<p>Total: <span id="total"><bean:write name="result" property="total"/></span></p>
<table id="results">
  <tr><th>Name</th><th>Email</th><th>City</th></tr>
  <logic:iterate id="c" name="result" property="items">
    <tr data-customer-id="<bean:write name="c" property="id"/>">
      <td><a href="${pageContext.request.contextPath}/customers/view.do?id=<bean:write name="c" property="id"/>"><bean:write name="c" property="lastName"/>, <bean:write name="c" property="firstName"/></a></td>
      <td><bean:write name="c" property="email"/></td>
      <td><bean:write name="c" property="city" ignore="true"/></td>
    </tr>
  </logic:iterate>
</table>
<jsp:include page="/WEB-INF/jsp/pager.jsp"/>
<p><a href="${pageContext.request.contextPath}/customers/edit.do">New customer</a></p>
