package org.mortbay.sailing.hpf.server;

import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import java.io.IOException;
import java.io.InputStream;

public class StaticResourceServlet extends HttpServlet
{
    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws IOException
    {
        String path = req.getPathInfo();
        if (path == null || "/".equals(path))
            path = "/index.html";

        InputStream in = getClass().getResourceAsStream("/admin" + path);
        if (in == null)
        {
            resp.sendError(404);
            return;
        }

        resp.setContentType(guessContentType(path));
        try (in)
        {
            in.transferTo(resp.getOutputStream());
        }
    }

    private String guessContentType(String path)
    {
        if (path.endsWith(".html"))
            return "text/html; charset=UTF-8";
        if (path.endsWith(".js"))
            return "application/javascript; charset=UTF-8";
        if (path.endsWith(".css"))
            return "text/css; charset=UTF-8";
        if (path.endsWith(".json"))
            return "application/json";
        if (path.endsWith(".png"))
            return "image/png";
        if (path.endsWith(".ico"))
            return "image/x-icon";
        return "application/octet-stream";
    }
}
