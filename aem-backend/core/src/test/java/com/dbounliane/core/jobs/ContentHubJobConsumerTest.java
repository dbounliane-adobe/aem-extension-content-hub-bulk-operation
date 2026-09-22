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
package com.dbounliane.core.jobs;

import org.apache.sling.api.resource.PersistenceException;
import org.apache.sling.api.resource.Resource;
import org.apache.sling.event.jobs.Job;
import org.apache.sling.event.jobs.consumer.JobConsumer;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import com.dbounliane.core.testcontext.AppAemContext;

import io.wcm.testing.mock.aem.junit5.AemContext;
import io.wcm.testing.mock.aem.junit5.AemContextExtension;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@ExtendWith(AemContextExtension.class)
class ContentHubJobConsumerTest {

    private final AemContext context = AppAemContext.newAemContext();

    private ContentHubJobConsumer consumer;

    @BeforeEach
    void setUp() {
        // Tree:
        // /content/dam/test/asset-a         (dam:Asset)
        // /content/dam/test/sub/asset-b     (dam:Asset)
        createAsset("/content/dam/test/asset-a");
        context.create().resource("/content/dam/test/sub", "jcr:primaryType", "sling:Folder");
        createAsset("/content/dam/test/sub/asset-b");

        consumer = context.registerInjectActivateService(new ContentHubJobConsumer());
    }

    @Test
    void nonRecursive_updatesOnlyTopLevelAssets() throws PersistenceException {
        int updated = consumer.applyToFolder(folder(), "approved", "delivery", false);

        assertEquals(1, updated);
        assertMetadata("/content/dam/test/asset-a", "approved", "delivery");
        // Asset in the sub-folder must be left untouched when not recursive.
        assertNull(metadata("/content/dam/test/sub/asset-b").getValueMap().get("dam:status", String.class));
    }

    @Test
    void recursive_updatesAssetsInSubFolders() throws PersistenceException {
        int updated = consumer.applyToFolder(folder(), "nostatus", "contenthub", true);

        assertEquals(2, updated);
        assertMetadata("/content/dam/test/asset-a", "nostatus", "contenthub");
        assertMetadata("/content/dam/test/sub/asset-b", "nostatus", "contenthub");
    }

    @Test
    void missingProperties_cancelsJob() {
        JobConsumer.JobResult result = consumer.process(
                job("/content/dam/test", null, "delivery", false));

        assertEquals(JobConsumer.JobResult.CANCEL, result);
    }

    private Resource folder() {
        return context.resourceResolver().getResource("/content/dam/test");
    }

    private void createAsset(String path) {
        context.create().resource(path, "jcr:primaryType", "dam:Asset");
        context.create().resource(path + "/jcr:content", "jcr:primaryType", "dam:AssetContent");
        context.create().resource(path + "/jcr:content/metadata", "jcr:primaryType", "nt:unstructured");
    }

    private Resource metadata(String assetPath) {
        context.resourceResolver().refresh();
        return context.resourceResolver().getResource(assetPath + "/jcr:content/metadata");
    }

    private void assertMetadata(String assetPath, String expectedStatus, String expectedTarget) {
        Resource md = metadata(assetPath);
        assertEquals(expectedStatus, md.getValueMap().get("dam:status", String.class));
        assertEquals(expectedTarget, md.getValueMap().get("dam:activationTarget", String.class));
    }

    private Job job(String path, String status, String target, boolean recursive) {
        Job job = mock(Job.class);
        when(job.getProperty(ContentHubJobConsumer.PN_PATH, String.class)).thenReturn(path);
        when(job.getProperty(ContentHubJobConsumer.PN_STATUS, String.class)).thenReturn(status);
        when(job.getProperty(ContentHubJobConsumer.PN_ACTIVATION_TARGET, String.class)).thenReturn(target);
        when(job.getProperty(ContentHubJobConsumer.PN_RECURSIVE, Boolean.FALSE)).thenReturn(recursive);
        return job;
    }
}
