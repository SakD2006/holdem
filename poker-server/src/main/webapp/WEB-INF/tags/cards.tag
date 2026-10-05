<%@ tag body-content="empty" pageEncoding="UTF-8" description="Draws a row of playing cards." %>
<%@ taglib prefix="c" uri="jakarta.tags.core" %>
<%@ attribute name="cards" required="true" type="java.util.List" description="CardView objects" %>
<%@ attribute name="none" required="false" description="What to show when there are no cards" %>
<c:choose>
  <c:when test="${empty cards}"><span class="muted"><c:out value="${none}"/></span></c:when>
  <c:otherwise><span class="cards"><c:forEach var="card" items="${cards}"><span class="card ${card.colour()}">${card.rank()}<span class="suit">${card.suit()}</span></span></c:forEach></span></c:otherwise>
</c:choose>
