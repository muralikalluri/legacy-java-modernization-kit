<%@ page contentType="text/html;charset=UTF-8" %>
<%@ taglib uri="http://struts.apache.org/tags-tiles" prefix="tiles" %>
<html>
<head>
  <title><tiles:getAsString name="title"/> - Harbor Claims Portal</title>
  <link rel="stylesheet" href="${pageContext.request.contextPath}/css/portal.css"/>
</head>
<body>
  <tiles:insert attribute="header"/>
  <div id="content">
    <tiles:insert attribute="body"/>
  </div>
  <tiles:insert attribute="footer"/>
</body>
</html>
