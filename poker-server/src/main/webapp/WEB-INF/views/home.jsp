<%@ page contentType="text/html; charset=UTF-8" pageEncoding="UTF-8" session="false" %>
<%@ taglib prefix="c" uri="jakarta.tags.core" %>
<%@ taglib prefix="fmt" uri="jakarta.tags.fmt" %>
<c:set var="pageTitle" value="Server"/>
<%@ include file="header.jspf" %>

<section class="hero">
  <p class="status"><span class="dot"></span> The server is running <span class="muted">&middot; version <c:out value="${version}"/></span></p>
  <h1>Join the game</h1>
  <c:choose>
    <c:when test="${empty addresses}">
      <p>This computer is not on a local network, so only this computer can play.
         In the desktop app, use the address <code>127.0.0.1</code>.</p>
    </c:when>
    <c:otherwise>
      <p>Open the Hold'em desktop app and press <strong>Find server</strong>, or type this address:</p>
      <ul class="addresses">
        <c:forEach var="address" items="${addresses}">
          <li><code><c:out value="${address}"/><c:if test="${port != 8080}">:<c:out value="${port}"/></c:if></code></li>
        </c:forEach>
      </ul>
      <p class="muted">Everyone must be on the same Wi-Fi or network as this computer.</p>
    </c:otherwise>
  </c:choose>
</section>

<section class="tiles">
  <div class="tile"><span class="number"><fmt:formatNumber value="${openRooms}"/></span><span class="caption">open room${openRooms == 1 ? '' : 's'}</span></div>
  <div class="tile"><span class="number"><fmt:formatNumber value="${handsPlayed}" groupingUsed="true"/></span><span class="caption">hand${handsPlayed == 1 ? '' : 's'} played</span></div>
  <a class="tile link" href="<c:url value='/leaderboard'/>"><span class="number">&#9733;</span><span class="caption">see the leaderboard</span></a>
</section>

<section>
  <h2>Look up a room</h2>
  <form class="lookup" method="get" action="<c:url value='/rooms'/>">
    <label for="code">Room code</label>
    <input id="code" name="code" maxlength="6" size="8" autocomplete="off" placeholder="ABC234" required>
    <button type="submit">Show results</button>
  </form>
</section>

<section>
  <h2>Latest rooms</h2>
  <c:choose>
    <c:when test="${empty rooms}">
      <p class="muted">No room has been created yet. Create one from the desktop app.</p>
    </c:when>
    <c:otherwise>
      <div class="scroll"><table>
        <thead><tr><th>Room</th><th>Code</th><th>Blinds</th><th>Created</th><th>Status</th></tr></thead>
        <tbody>
        <c:forEach var="room" items="${rooms}">
          <tr>
            <td><a href="<c:url value='/rooms/${room.code()}'/>"><c:out value="${room.name()}"/></a></td>
            <td><code><c:out value="${room.code()}"/></code></td>
            <td><fmt:formatNumber value="${room.smallBlind()}" groupingUsed="true"/> / <fmt:formatNumber value="${room.bigBlind()}" groupingUsed="true"/></td>
            <td><fmt:formatDate value="${room.created()}" pattern="d MMM, HH:mm"/></td>
            <td><span class="tag ${room.open() ? 'live' : ''}"><c:out value="${room.status()}"/></span></td>
          </tr>
        </c:forEach>
        </tbody>
      </table></div>
    </c:otherwise>
  </c:choose>
</section>

<%@ include file="footer.jspf" %>
