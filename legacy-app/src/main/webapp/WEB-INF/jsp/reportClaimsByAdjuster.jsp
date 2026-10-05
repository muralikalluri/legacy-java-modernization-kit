<%@ page contentType="text/html;charset=UTF-8" %>
<%@ taglib uri="http://struts.apache.org/tags-bean" prefix="bean" %>
<%@ taglib uri="http://struts.apache.org/tags-logic" prefix="logic" %>
<h2>Claims by adjuster</h2>
<table id="results">
  <tr><th>Adjuster</th><th>Claims</th><th>Open</th><th>Open %</th><th>Total claimed</th><th>Workload</th></tr>
  <logic:iterate id="r" name="rows">
    <tr>
      <td><bean:write name="r" property="name"/></td>
      <td><bean:write name="r" property="count"/></td>
      <td><bean:write name="r" property="open"/></td>
      <td><bean:write name="r" property="openPercent"/>%</td>
      <td><bean:write name="r" property="totalDisplay"/></td>
      <td><bean:write name="r" property="workload"/></td>
    </tr>
  </logic:iterate>
</table>
<p id="busiest">Busiest adjuster: <bean:write name="busiest"/></p>
