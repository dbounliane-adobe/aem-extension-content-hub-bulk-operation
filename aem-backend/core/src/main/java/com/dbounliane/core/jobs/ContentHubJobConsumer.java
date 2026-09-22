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

import java.util.Iterator;
import java.util.Map;

import org.apache.sling.api.resource.LoginException;
import org.apache.sling.api.resource.ModifiableValueMap;
import org.apache.sling.api.resource.PersistenceException;
import org.apache.sling.api.resource.Resource;
import org.apache.sling.api.resource.ResourceResolver;
import org.apache.sling.api.resource.ResourceResolverFactory;
import org.apache.sling.event.jobs.Job;
import org.apache.sling.event.jobs.consumer.JobConsumer;
import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.Reference;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.day.cq.dam.api.DamConstants;

/**
 * Applies a review status ({@code dam:status}) and an activation target
 * ({@code dam:activationTarget}) to every asset found under a DAM folder,
 * optionally recursing into sub-folders. Runs asynchronously so that large
 * folder trees do not block the triggering HTTP request.
 *
 * <p>The job is enqueued by {@code ContentHubActionServlet} on the topic
 * {@link #TOPIC}. The following job properties are expected:</p>
 * <ul>
 *   <li>{@link #PN_PATH} - the DAM folder path to process</li>
 *   <li>{@link #PN_STATUS} - the {@code dam:status} value to set</li>
 *   <li>{@link #PN_ACTIVATION_TARGET} - the {@code dam:activationTarget} value to set</li>
 *   <li>{@link #PN_RECURSIVE} - whether to descend into sub-folders</li>
 * </ul>
 */
@Component(
        service = JobConsumer.class,
        property = { JobConsumer.PROPERTY_TOPICS + "=" + ContentHubJobConsumer.TOPIC })
public class ContentHubJobConsumer implements JobConsumer {

    public static final String TOPIC = "dbounliane/contenthub";

    public static final String PN_PATH = "path";
    public static final String PN_STATUS = "status";
    public static final String PN_ACTIVATION_TARGET = "activationTarget";
    public static final String PN_RECURSIVE = "recursive";

    /** Sub-service name mapped to the system user in the ServiceUserMapper. */
    static final String SUBSERVICE = "contenthub";

    /** Review status property on the asset's metadata node. */
    static final String PN_DAM_STATUS = "dam:status";

    /** Property written on the asset's metadata node to carry the activation target. */
    static final String PN_DAM_ACTIVATION_TARGET = "dam:activationTarget";

    /** Number of mutations between intermediate commits. */
    private static final int COMMIT_BATCH_SIZE = 100;

    private static final Logger LOG = LoggerFactory.getLogger(ContentHubJobConsumer.class);

    @Reference
    private ResourceResolverFactory resolverFactory;

    @Override
    public JobResult process(final Job job) {
        final String path = job.getProperty(PN_PATH, String.class);
        final String status = job.getProperty(PN_STATUS, String.class);
        final String activationTarget = job.getProperty(PN_ACTIVATION_TARGET, String.class);
        final boolean recursive = job.getProperty(PN_RECURSIVE, Boolean.FALSE);

        if (path == null || status == null || activationTarget == null) {
            LOG.error("Content Hub job is missing required properties (path={}, status={}, activationTarget={})",
                    path, status, activationTarget);
            return JobResult.CANCEL;
        }

        final Map<String, Object> authInfo = Map.of(ResourceResolverFactory.SUBSERVICE, SUBSERVICE);
        try (ResourceResolver resolver = resolverFactory.getServiceResourceResolver(authInfo)) {
            final Resource folder = resolver.getResource(path);
            if (folder == null) {
                LOG.error("Content Hub job target folder does not exist: {}", path);
                return JobResult.CANCEL;
            }

            final int updated = applyToFolder(folder, status, activationTarget, recursive);
            LOG.info("Content Hub job finished for {}: {} asset(s) updated (recursive={})",
                    path, updated, recursive);
            return JobResult.OK;
        } catch (LoginException e) {
            LOG.error("Content Hub job could not obtain the '{}' service user. "
                    + "Check the ServiceUserMapper amendment and repoinit ACLs.", SUBSERVICE, e);
            return JobResult.FAILED;
        } catch (PersistenceException e) {
            LOG.error("Content Hub job failed to persist changes for {}", path, e);
            return JobResult.FAILED;
        }
    }

    /**
     * Applies the status and activation target to the assets under {@code folder}
     * and commits the changes. Package-visible for unit testing.
     *
     * @return the number of assets updated
     */
    int applyToFolder(final Resource folder, final String status, final String activationTarget,
            final boolean recursive) throws PersistenceException {
        final Counter counter = new Counter();
        processFolder(folder, status, activationTarget, recursive, counter);
        final ResourceResolver resolver = folder.getResourceResolver();
        if (resolver.hasChanges()) {
            resolver.commit();
        }
        return counter.updated;
    }

    private void processFolder(final Resource folder, final String status, final String activationTarget,
            final boolean recursive, final Counter counter) throws PersistenceException {
        final ResourceResolver resolver = folder.getResourceResolver();
        for (final Iterator<Resource> it = folder.listChildren(); it.hasNext();) {
            final Resource child = it.next();
            if (isAsset(child)) {
                if (applyToAsset(child, status, activationTarget)) {
                    counter.updated++;
                    if (counter.updated % COMMIT_BATCH_SIZE == 0 && resolver.hasChanges()) {
                        resolver.commit();
                    }
                }
            } else if (recursive && isFolder(child)) {
                processFolder(child, status, activationTarget, recursive, counter);
            }
        }
    }

    private boolean applyToAsset(final Resource asset, final String status, final String activationTarget) {
        final Resource metadata = asset.getChild("jcr:content/metadata");
        if (metadata == null) {
            LOG.debug("Skipping asset without metadata node: {}", asset.getPath());
            return false;
        }
        final ModifiableValueMap mvm = metadata.adaptTo(ModifiableValueMap.class);
        if (mvm == null) {
            LOG.warn("Metadata node is not modifiable, skipping: {}", metadata.getPath());
            return false;
        }
        mvm.put(PN_DAM_STATUS, status);
        mvm.put(PN_DAM_ACTIVATION_TARGET, activationTarget);
        return true;
    }

    private boolean isAsset(final Resource resource) {
        return resource.isResourceType(DamConstants.NT_DAM_ASSET);
    }

    private boolean isFolder(final Resource resource) {
        return resource.isResourceType("sling:Folder")
                || resource.isResourceType("sling:OrderedFolder")
                || resource.isResourceType("nt:folder");
    }

    /** Mutable counter carried through the recursion. */
    private static final class Counter {
        private int updated;
    }
}
