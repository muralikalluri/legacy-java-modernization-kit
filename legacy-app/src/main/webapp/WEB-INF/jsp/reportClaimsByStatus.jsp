<%@ page contentType="text/html;charset=UTF-8" %>
<%@ taglib uri="http://struts.apache.org/tags-bean" prefix="bean" %>
<%@ taglib uri="http://struts.apache.org/tags-logic" prefix="logic" %>
<h2>Claims by status</h2>
<table id="results">
  <tr><th>Status</th><th>Claims</th><th>Total claimed</th><th>Share</th></tr>
  <logic:iterate id="r" name="rows">
    <tr data-status="<bean:write name="r" property="status"/>">
      <td><bean:write name="r" property="label"/></td>
      <td class="count"><bean:write name="r" property="count"/></td>
      <td><bean:write name="r" property="totalDisplay"/></td>
      <td><bean:write name="r" property="share"/></td>
    </tr>
  </logic:iterate>
  <tr id="grand-total"><th>All</th><th><bean:write name="grandCount"/></th><th><bean:write name="grandTotal"/></th><th></th></tr>
</table>
<p><a href="${pageContext.request.contextPath}/reports/claimsByStatusPdf.do">Download PDF</a></p>
