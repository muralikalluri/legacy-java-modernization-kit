<%@ page contentType="text/html;charset=UTF-8" isErrorPage="true" %>
<%
    Object code = request.getAttribute("javax.servlet.error.status_code");
    int status = code == null ? 500 : ((Integer) code).intValue();
    String text = status == 404 ? "Not found" : status == 403 ? "Access denied" : "Something went wrong";
%>
<html>
<head><title>Error - Harbor Claims Portal</title></head>
<body>
  <h2 id="error-title"><%= status %> <%= text %></h2>
  <p><a href="<%= request.getContextPath() %>/home.do">Back to home</a></p>
</body>
</html>
