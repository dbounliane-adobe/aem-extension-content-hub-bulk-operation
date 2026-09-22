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
import java.util.HashMap;
import java.util.Map;

import javax.servlet.http.HttpServletResponse;

import org.apache.sling.event.jobs.Job;
import org.apache.sling.event.jobs.JobManager;
import org.apache.sling.testing.mock.sling.servlet.MockSlingHttpServletRequest;
import org.apache.sling.testing.mock.sling.servlet.MockSlingHttpServletResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import com.dbounliane.core.jobs.ContentHubJobConsumer;
import com.dbounliane.core.testcontext.AppAemContext;

import io.wcm.testing.mock.aem.junit5.AemContext;
import io.wcm.testing.mock.aem.junit5.AemContextExtension;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(AemContextExtension.class)
class ContentHubActionServletTest {

    private static final String FOLDER = "/content/dam/test";

    private final AemContext context = AppAemContext.newAemContext();

    private ContentHubActionServlet servlet;
    private JobManager jobManager;

    @BeforeEach
    void setUp() {
        context.create().resource(FOLDER, "jcr:primaryType", "sling:OrderedFolder");

        jobManager = mock(JobManager.class);
        final Job job = mock(Job.class);
        when(job.getId()).thenReturn("job-123");
        when(jobManager.addJob(eq(ContentHubJobConsumer.TOPIC), anyMap())).thenReturn(job);
        context.registerService(JobManager.class, jobManager);

        servlet = context.registerInjectActivateService(new ContentHubActionServlet());
    }

    @Test
    void validRequest_enqueuesJobAndReturnsAccepted() throws IOException {
        final MockSlingHttpServletResponse response = post("approved", "delivery", "true");

        assertEquals(HttpServletResponse.SC_ACCEPTED, response.getStatus());
        assertTrue(response.getOutputAsString().contains("job-123"));
        assertTrue(response.getOutputAsString().contains("\"accepted\":true"));
        verify(jobManager).addJob(eq(ContentHubJobConsumer.TOPIC), anyMap());
    }

    @Test
    void invalidStatus_returnsBadRequestAndDoesNotEnqueue() throws IOException {
        final MockSlingHttpServletResponse response = post("bogus", "delivery", "false");

        assertEquals(HttpServletResponse.SC_BAD_REQUEST, response.getStatus());
        verify(jobManager, never()).addJob(eq(ContentHubJobConsumer.TOPIC), anyMap());
    }

    @Test
    void invalidActivationTarget_returnsBadRequest() throws IOException {
        final MockSlingHttpServletResponse response = post("approved", "bogus", "false");

        assertEquals(HttpServletResponse.SC_BAD_REQUEST, response.getStatus());
        verify(jobManager, never()).addJob(eq(ContentHubJobConsumer.TOPIC), anyMap());
    }

    private MockSlingHttpServletResponse post(String status, String target, String recursive)
            throws IOException {
        final MockSlingHttpServletRequest request = context.request();
        request.setResource(context.resourceResolver().getResource(FOLDER));

        final Map<String, Object> params = new HashMap<>();
        params.put(ContentHubJobConsumer.PN_STATUS, status);
        params.put(ContentHubJobConsumer.PN_ACTIVATION_TARGET, target);
        params.put(ContentHubJobConsumer.PN_RECURSIVE, recursive);
        request.setParameterMap(params);

        final MockSlingHttpServletResponse response = context.response();
        servlet.doPost(request, response);
        return response;
    }
}
