<%@ page contentType="text/html;charset=UTF-8" %>
<h2>Reports</h2>
<ul>
  <li><a href="${pageContext.request.contextPath}/reports/claimsByStatus.do">Claims by status</a>
      (<a href="${pageContext.request.contextPath}/reports/claimsByStatusPdf.do">PDF</a>)</li>
  <li><a href="${pageContext.request.contextPath}/reports/claimsByAdjuster.do">Claims by adjuster</a></li>
  <li><a href="${pageContext.request.contextPath}/reports/policyExpiry.do">Policy expiry</a></li>
</ul>
