<%@ page contentType="text/html; charset=UTF-8" pageEncoding="UTF-8" session="false" isErrorPage="true" %>
<%@ taglib prefix="c" uri="jakarta.tags.core" %>
<c:set var="pageTitle" value="Not found"/>
<%@ include file="header.jspf" %>

<h1>Nothing here</h1>
<p><c:out value="${empty message ? 'There is no page at this address.' : message}"/></p>
<p><a href="<c:url value='/'/>">Go to the front page</a></p>

<%@ include file="footer.jspf" %>
