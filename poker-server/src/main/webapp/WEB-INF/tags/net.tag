<%@ tag body-content="empty" pageEncoding="UTF-8" description="Chips won or lost: green with a plus sign, or red." %>
<%@ taglib prefix="fmt" uri="jakarta.tags.fmt" %>
<%@ attribute name="value" required="true" type="java.lang.Long" %>
<span class="${value > 0 ? 'up' : (value < 0 ? 'down' : 'level')}">${value > 0 ? '+' : ''}<fmt:formatNumber value="${value}" groupingUsed="true"/></span>
