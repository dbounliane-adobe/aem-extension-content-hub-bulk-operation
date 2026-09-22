/*
 *  Copyright 2026 Adobe Systems Incorporated
 *
 *  Licensed under the Apache License, Version 2.0 (the "License");
 *  you may not use this file except in compliance with the License.
 *  You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 */
package com.dbounliane.core.servlets;

import java.io.IOException;
import java.util.Map;
import java.util.Set;

import javax.servlet.Servlet;

import org.apache.sling.api.SlingHttpServletRequest;
import org.apache.sling.api.SlingHttpServletResponse;
import org.apache.sling.api.servlets.HttpConstants;
import org.apache.sling.api.servlets.SlingAllMethodsServlet;
import org.apache.sling.event.jobs.Job;
import org.apache.sling.event.jobs.JobManager;
import org.apache.sling.servlets.annotations.SlingServletResourceTypes;
import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.Reference;
import org.osgi.service.component.propertytypes.ServiceDescription;

import com.dbounliane.core.jobs.ContentHubJobConsumer;

/**
 * Receives the Content Hub bulk-approval request from the App Builder Assets
 * extension and enqueues an asynchronous {@link Job} that performs the actual
 * mutation (see {@link ContentHubJobConsumer}).
 *
 * <p>Bound to DAM folder resource types with the {@code contenthub} selector and
 * the {@code json} extension, so the extension calls
 * {@code POST /content/dam/&lt;folder&gt;.contenthub.json}. Binding to the resource
 * type (rather than a {@code /bin} path) is the AEM as a Cloud Service
 * recommended approach and avoids servlet path allow-listing.</p>
 */
@Component(service = { Servlet.class })
@SlingServletResourceTypes(
        resourceTypes = { "sling:Folder", "sling:OrderedFolder" },
        selectors = "contenthub",
        extensions = "json",
        methods = HttpConstants.METHOD_POST)
@ServiceDescription("Content Hub bulk approval / activation-target servlet")
public class ContentHubActionServlet extends SlingAllMethodsServlet {

    private static final long serialVersionUID = 1L;

    /** Allowed dam:status values. */
    private static final Set<String> ALLOWED_STATUS = Set.of("approved", "nostatus");
    /** Allowed dam:activationTarget values. */
    private static final Set<String> ALLOWED_TARGET = Set.of("delivery", "contenthub");

    @Reference
    private transient JobManager jobManager;

    @Override
    protected void doPost(final SlingHttpServletRequest req, final SlingHttpServletResponse resp)
            throws IOException {
        resp.setContentType("application/json");
        resp.setCharacterEncoding("UTF-8");

        final String path = req.getResource().getPath();
        final String status = req.getParameter(ContentHubJobConsumer.PN_STATUS);
        final String activationTarget = req.getParameter(ContentHubJobConsumer.PN_ACTIVATION_TARGET);
        final boolean recursive = Boolean.parseBoolean(req.getParameter(ContentHubJobConsumer.PN_RECURSIVE));

        if (!ALLOWED_STATUS.contains(status)) {
            writeError(resp, SlingHttpServletResponse.SC_BAD_REQUEST,
                    "Invalid or missing 'status' (expected one of " + ALLOWED_STATUS + ")");
            return;
        }
        if (!ALLOWED_TARGET.contains(activationTarget)) {
            writeError(resp, SlingHttpServletResponse.SC_BAD_REQUEST,
                    "Invalid or missing 'activationTarget' (expected one of " + ALLOWED_TARGET + ")");
            return;
        }

        final Map<String, Object> props = Map.of(
                ContentHubJobConsumer.PN_PATH, path,
                ContentHubJobConsumer.PN_STATUS, status,
                ContentHubJobConsumer.PN_ACTIVATION_TARGET, activationTarget,
                ContentHubJobConsumer.PN_RECURSIVE, recursive);

        final Job job = jobManager.addJob(ContentHubJobConsumer.TOPIC, props);
        if (job == null) {
            writeError(resp, SlingHttpServletResponse.SC_INTERNAL_SERVER_ERROR,
                    "Could not enqueue the Content Hub job");
            return;
        }

        final StringBuilder out = new StringBuilder("{");
        out.append("\"accepted\":true,");
        out.append("\"jobId\":\"").append(jsonEscape(job.getId())).append("\",");
        out.append("\"path\":\"").append(jsonEscape(path)).append("\",");
        out.append("\"status\":\"").append(jsonEscape(status)).append("\",");
        out.append("\"activationTarget\":\"").append(jsonEscape(activationTarget)).append("\",");
        out.append("\"recursive\":").append(recursive);
        out.append("}");
        resp.setStatus(SlingHttpServletResponse.SC_ACCEPTED);
        resp.getWriter().write(out.toString());
    }

    private void writeError(final SlingHttpServletResponse resp, final int code, final String message)
            throws IOException {
        resp.setStatus(code);
        resp.getWriter().write("{\"accepted\":false,\"error\":\"" + jsonEscape(message) + "\"}");
    }

    private static String jsonEscape(final String value) {
        if (value == null) {
            return "";
        }
        return value.replace("\\", "\\\\").replace("\"", "\\\"");
    }
}
