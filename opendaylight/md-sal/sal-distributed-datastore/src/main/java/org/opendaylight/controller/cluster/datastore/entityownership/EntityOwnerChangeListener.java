/*
 * Copyright (c) 2015 Brocade Communications Systems, Inc. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.controller.cluster.datastore.entityownership;

import static org.opendaylight.controller.cluster.datastore.entityownership.EntityOwnersModel.createEntity;
import com.google.common.base.Optional;
import com.google.common.base.Preconditions;
import com.google.common.base.Strings;
import com.google.common.base.Verify;
import java.util.Collection;
import java.util.Objects;
import org.opendaylight.controller.cluster.access.concepts.MemberName;
import org.opendaylight.controller.md.sal.common.api.clustering.Entity;
import org.opendaylight.yangtools.yang.data.api.schema.LeafNode;
import org.opendaylight.yangtools.yang.data.api.schema.NormalizedNode;
import org.opendaylight.yangtools.yang.data.api.schema.tree.DataTreeCandidate;
import org.opendaylight.yangtools.yang.data.api.schema.tree.DataTreeCandidateNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Listens for entity owner changes and notifies the EntityOwnershipListenerSupport appropriately.
 *
 * @author Thomas Pantelis
 */
class EntityOwnerChangeListener extends AbstractEntityOwnerChangeListener {
    private static final Logger LOG = LoggerFactory.getLogger(EntityOwnerChangeListener.class);

    private final String localMemberName;
    private final EntityOwnershipListenerSupport listenerSupport;

    EntityOwnerChangeListener(final MemberName localMemberName, final EntityOwnershipListenerSupport listenerSupport) {
        this.localMemberName = Verify.verifyNotNull(localMemberName.getName());
        this.listenerSupport = Preconditions.checkNotNull(listenerSupport);
    }

    @Override
    public void onDataTreeChanged(final Collection<DataTreeCandidate> changes) {
        for(DataTreeCandidate change: changes) {
            DataTreeCandidateNode changeRoot = change.getRootNode();
            LeafNode<?> ownerLeaf = (LeafNode<?>) changeRoot.getDataAfter().get();

            LOG.debug("{}: Entity node changed: {}, {}", logId(), changeRoot.getModificationType(), change.getRootPath());

            String newOwner = extractOwner(ownerLeaf);

            String origOwner = null;
            Optional<NormalizedNode<?, ?>> dataBefore = changeRoot.getDataBefore();
            if(dataBefore.isPresent()) {
                origOwner = extractOwner((LeafNode<?>) changeRoot.getDataBefore().get());
            }

            LOG.debug("{}: New owner: {}, Original owner: {}", logId(), newOwner, origOwner);

            if (!Objects.equals(origOwner, newOwner)) {
                boolean isOwner = localMemberName.equals(newOwner);
                boolean wasOwner = localMemberName.equals(origOwner);
                boolean hasOwner = !Strings.isNullOrEmpty(newOwner);

                Entity entity = createEntity(change.getRootPath());

                LOG.debug("{}: Calling notifyEntityOwnershipListeners: entity: {}, wasOwner: {}, isOwner: {}, hasOwner: {}",
                        logId(), entity, wasOwner, isOwner, hasOwner);

                listenerSupport.notifyEntityOwnershipListeners(entity, wasOwner, isOwner, hasOwner);
            }
        }
    }

    private String logId() {
        return listenerSupport.getLogId();
    }
}
