package com.saksham.poker.server.web;

import com.saksham.poker.common.error.ErrorCode;
import com.saksham.poker.common.exception.PokerException;
import com.saksham.poker.server.bootstrap.AppContext;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * What every web page's controller shares. A subclass gathers the data its page shows, stores it on
 * the request and names the JSP to draw it; this class hands over to that JSP, and turns anything
 * that goes wrong into the error page. Pages are for reading only, so they answer only GET.
 */
public abstract class PageServlet extends HttpServlet {

    private static final long serialVersionUID = 1L;
    private static final Logger log = LoggerFactory.getLogger(PageServlet.class);

    /** JSPs live under WEB-INF so a browser can reach them only through their servlet. */
    private static final String VIEWS = "/WEB-INF/views/";

    @Override
    protected final void doGet(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {
        String view;
        try {
            view = prepare(request, response);
        } catch (PokerException e) {
            // On a page, a room or hand that does not exist is simply "nothing at this address".
            showError(request, response, e.code() == ErrorCode.INTERNAL
                    ? HttpServletResponse.SC_INTERNAL_SERVER_ERROR : HttpServletResponse.SC_NOT_FOUND, e.getMessage());
            return;
        } catch (RuntimeException e) {
            log.error("GET {} failed", request.getRequestURI(), e);
            showError(request, response, HttpServletResponse.SC_INTERNAL_SERVER_ERROR,
                    "Something went wrong on the server. Try again; if it keeps happening, check the server log.");
            return;
        }
        if (view != null) {
            request.getRequestDispatcher(VIEWS + view).forward(request, response);
        }
    }

    /**
     * Stores what the page shows as request attributes.
     *
     * @return the JSP to draw, such as {@code "home.jsp"}; or null if the response has already been
     *     answered, with a redirect say
     * @throws PokerException if what was asked for does not exist; the message is shown to the visitor
     */
    protected abstract String prepare(HttpServletRequest request, HttpServletResponse response)
            throws PokerException, IOException;

    /** The server's shared services. */
    protected AppContext app() {
        return AppContext.from(getServletContext());
    }

    private static void showError(HttpServletRequest request, HttpServletResponse response, int status,
            String message) throws ServletException, IOException {
        response.setStatus(status);
        request.setAttribute("message", message);
        request.getRequestDispatcher(VIEWS + "error.jsp").forward(request, response);
    }
}
