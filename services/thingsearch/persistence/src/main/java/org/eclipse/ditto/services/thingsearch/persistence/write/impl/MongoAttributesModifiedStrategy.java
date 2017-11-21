/*
 * Copyright (c) 2017 Bosch Software Innovations GmbH.
 *
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v2.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/org/documents/epl-2.0/index.php
 *
 * Contributors:
 *    Bosch Software Innovations GmbH - initial contribution
 */
package org.eclipse.ditto.services.thingsearch.persistence.write.impl;

import java.util.Collections;
import java.util.List;

import org.bson.conversions.Bson;
import org.eclipse.ditto.model.policiesenforcers.PolicyEnforcer;
import org.eclipse.ditto.services.thingsearch.persistence.write.IndexLengthRestrictionEnforcer;
import org.eclipse.ditto.signals.events.things.AttributesModified;

/**
 * Strategy that creates {@link Bson} for {@link AttributesModified} events.
 */
public final class MongoAttributesModifiedStrategy extends MongoEventToPersistenceStrategy<AttributesModified> {

    /**
     * {@inheritDoc}
     */
    @Override
    public final List<Bson> thingUpdates(final AttributesModified event,
            final IndexLengthRestrictionEnforcer indexLengthRestrictionEnforcer) {
        return AttributesUpdateFactory.createAttributesUpdate(indexLengthRestrictionEnforcer,
                event.getModifiedAttributes());
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public final List<PolicyUpdate> policyUpdates(final AttributesModified event, final PolicyEnforcer policyEnforcer) {
        if (isPolicyRevelant(event.getImplementedSchemaVersion())) {
            return Collections.singletonList(
                    PolicyUpdateFactory.createAttributesUpdate(event.getThingId(),
                            event.getModifiedAttributes(),
                            policyEnforcer));
        }
        return Collections.emptyList();
    }
}
