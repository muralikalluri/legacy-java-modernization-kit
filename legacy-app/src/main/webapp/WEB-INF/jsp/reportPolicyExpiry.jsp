<%@ page contentType="text/html;charset=UTF-8" %>
<%@ taglib uri="http://struts.apache.org/tags-html" prefix="html" %>
<%@ taglib uri="http://struts.apache.org/tags-bean" prefix="bean" %>
<%@ taglib uri="http://struts.apache.org/tags-logic" prefix="logic" %>
<h2>Policy expiry</h2>
<logic:messagesPresent>
  <div id="errors"><html:errors/></div>
</logic:messagesPresent>
<form method="get" action="${pageContext.request.contextPath}/reports/policyExpiry.do">
  As of (yyyy-MM-dd) <input type="text" name="asOf" value="<bean:write name="asOf" ignore="true"/>"/>
  Days <input type="text" name="days" value="<bean:write name="days" ignore="true"/>" size="4"/>
  <input type="submit" value="Show"/>
</form>
<logic:present name="untilDate">
  <p>Active policies ending from <bean:write name="asOf"/> to <bean:write name="untilDate"/>, inclusive.</p>
</logic:present>
<table id="results">
  <tr><th>Policy</th><th>Customer</th><th>Ends</th><th>Coverage limit</th></tr>
  <logic:iterate id="p" name="policies">
    <tr data-policy-id="<bean:write name="p" property="id"/>">
      <td><a href="${pageContext.request.contextPath}/policy/view.do?id=<bean:write name="p" property="id"/>"><bean:write name="p" property="policyNumber"/></a></td>
      <td><bean:write name="p" property="customerName"/></td>
      <td><bean:write name="p" property="endDate"/></td>
      <td><bean:write name="p" property="coverageLimitDisplay"/></td>
    </tr>
  </logic:iterate>
</table>
