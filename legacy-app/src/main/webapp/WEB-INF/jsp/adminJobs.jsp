<%@ page contentType="text/html;charset=UTF-8" %>
<%@ taglib uri="http://struts.apache.org/tags-html" prefix="html" %>
<%@ taglib uri="http://struts.apache.org/tags-bean" prefix="bean" %>
<%@ taglib uri="http://struts.apache.org/tags-logic" prefix="logic" %>
<h2>Batch jobs</h2>
<logic:messagesPresent>
  <div id="errors"><html:errors/></div>
</logic:messagesPresent>
<logic:equal name="busy" value="true">
  <p id="busy">A run is already in progress.</p>
</logic:equal>
<p>Policy expiry (lapses ACTIVE policies whose end date has passed).
  Nightly schedule: <span id="scheduled"><logic:equal name="scheduled" value="true">on, next run <bean:write name="nextFire"/></logic:equal><logic:equal name="scheduled" value="false">off</logic:equal></span></p>
<form method="post" action="${pageContext.request.contextPath}/admin/jobRun.do">
  As of (blank means today, yyyy-MM-dd) <input type="text" name="asOf"/>
  <input type="submit" value="Run now"/>
</form>
<h3>Recent runs</h3>
<table id="runs">
  <tr><th>Run</th><th>Trigger</th><th>By</th><th>As of</th><th>Status</th><th>Policies lapsed</th><th>Error</th></tr>
  <logic:iterate id="r" name="runs">
    <tr data-run-id="<bean:write name="r" property="id"/>" data-status="<bean:write name="r" property="status"/>">
      <td><bean:write name="r" property="id"/></td>
      <td><bean:write name="r" property="triggerType"/></td>
      <td><bean:write name="r" property="triggeredBy"/></td>
      <td><bean:write name="r" property="asOf"/></td>
      <td><bean:write name="r" property="status"/></td>
      <td class="rows"><bean:write name="r" property="rowsAffected" ignore="true"/></td>
      <td><bean:write name="r" property="error" ignore="true"/></td>
    </tr>
  </logic:iterate>
</table>
