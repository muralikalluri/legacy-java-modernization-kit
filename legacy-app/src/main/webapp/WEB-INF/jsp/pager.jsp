<%@ page contentType="text/html;charset=UTF-8" %>
<%@ taglib uri="http://struts.apache.org/tags-bean" prefix="bean" %>
<%@ taglib uri="http://struts.apache.org/tags-logic" prefix="logic" %>
<div id="pager">
  Page <bean:write name="result" property="page"/> of <bean:write name="result" property="totalPages"/>
  <logic:equal name="result" property="hasPrev" value="true">
    <a id="prev" href="${pageContext.request.contextPath}<bean:write name="pagerBase"/><bean:write name="result" property="prevPage"/>">Previous</a>
  </logic:equal>
  <logic:equal name="result" property="hasNext" value="true">
    <a id="next" href="${pageContext.request.contextPath}<bean:write name="pagerBase"/><bean:write name="result" property="nextPage"/>">Next</a>
  </logic:equal>
</div>
