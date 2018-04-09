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
package org.eclipse.ditto.services.connectivity.messaging.persistence;

import java.util.Objects;
import java.util.Optional;
import java.util.function.Predicate;

import javax.annotation.Nullable;

import org.eclipse.ditto.json.JsonFactory;
import org.eclipse.ditto.json.JsonField;
import org.eclipse.ditto.json.JsonFieldDefinition;
import org.eclipse.ditto.json.JsonFieldSelector;
import org.eclipse.ditto.json.JsonObject;
import org.eclipse.ditto.json.JsonObjectBuilder;
import org.eclipse.ditto.json.JsonParseException;
import org.eclipse.ditto.model.base.json.FieldType;
import org.eclipse.ditto.model.base.json.JsonSchemaVersion;
import org.eclipse.ditto.model.base.json.Jsonifiable;
import org.eclipse.ditto.model.connectivity.Connection;
import org.eclipse.ditto.model.connectivity.ConnectionStatus;
import org.eclipse.ditto.model.connectivity.ConnectivityModelFactory;
import org.eclipse.ditto.model.connectivity.MappingContext;

/**
 * Data representing the state of a Connection which is used in order to persist snapshot state into MongoDB.
 */
public final class ConnectionData implements Jsonifiable.WithFieldSelectorAndPredicate<JsonField> {

    private static final JsonFieldDefinition<JsonObject> CONNECTION =
            JsonFactory.newJsonObjectFieldDefinition("connection", FieldType.REGULAR, JsonSchemaVersion.V_1,
                    JsonSchemaVersion.V_2);

    private static final JsonFieldDefinition<String> CONNECTION_STATUS =
            JsonFactory.newStringFieldDefinition("connectionStatus", FieldType.REGULAR, JsonSchemaVersion.V_1,
                    JsonSchemaVersion.V_2);

    private static final JsonFieldDefinition<JsonObject> MAPPING_CONTEXT =
            JsonFactory.newJsonObjectFieldDefinition("mappingContext", FieldType.REGULAR, JsonSchemaVersion.V_1,
                    JsonSchemaVersion.V_2);


    private final Connection connection;
    private final ConnectionStatus connectionStatus;
    @Nullable private final MappingContext mappingContext;

    /**
     * Constructs a new connection data instance.
     * @param connection Connection information of the connection data.
     * @param connectionStatus ConnectionStatus information of the connection data.
     * @param mappingContext the mapping context
     */
    public ConnectionData(final Connection connection, final ConnectionStatus connectionStatus,
            @Nullable final MappingContext mappingContext) {

        this.connection = connection;
        this.connectionStatus = connectionStatus;
        this.mappingContext = mappingContext;
    }

    /**
     * @return the Connection information of the connection data.
     */
    public Connection getConnection() {
        return connection;
    }

    /**
     * @return the ConnectionStatus information of the connection data.
     */
    public ConnectionStatus getConnectionStatus() {
        return connectionStatus;
    }

    /**
     * @return the mapping context configured for this connection.
     */
    public Optional<MappingContext> getMappingContext() {
        return Optional.ofNullable(mappingContext);
    }

    /**
     * Creates a new {@code ConnectionData} object from the specified JSON object.
     *
     * @param jsonObject a JSON object which provides the data for the ConnectionData to be created.
     * @return a new ConnectionData which is initialised with the extracted data from {@code jsonObject}.
     * @throws NullPointerException if {@code jsonObject} is {@code null}.
     * @throws org.eclipse.ditto.json.JsonParseException if {@code jsonObject} is not an appropriate JSON object.
     */
    static ConnectionData fromJson(final JsonObject jsonObject) {

        final JsonObject readConnection = jsonObject.getValueOrThrow(CONNECTION);
        final Connection connection = ConnectivityModelFactory.connectionFromJson(readConnection);

        final String readConnectionStatus = jsonObject.getValueOrThrow(CONNECTION_STATUS);
        final ConnectionStatus connectionStatus =
                ConnectionStatus.forName(readConnectionStatus).orElseThrow(() -> JsonParseException.newBuilder()
                        .message("Could not create ConnectionStatus from: " + jsonObject)
                        .build());

        final JsonObject readMappingContext = jsonObject.getValue(MAPPING_CONTEXT).orElse(null);
        final MappingContext mappingContext = readMappingContext != null ?
                ConnectivityModelFactory.mappingContextFromJson(readMappingContext) : null;

        return new ConnectionData(connection, connectionStatus, mappingContext);
    }

    @Override
    public JsonObject toJson() {
        return toJson(FieldType.notHidden());
    }

    @Override
    public JsonObject toJson(final JsonSchemaVersion schemaVersion, final Predicate<JsonField> thePredicate) {

        final Predicate<JsonField> predicate = schemaVersion.and(thePredicate);
        final JsonObjectBuilder jsonObjectBuilder = JsonFactory.newObjectBuilder();

        jsonObjectBuilder.set(CONNECTION, connection.toJson(schemaVersion, thePredicate), predicate);
        jsonObjectBuilder.set(CONNECTION_STATUS, connectionStatus.getName(), predicate);
        if (mappingContext != null) {
            jsonObjectBuilder.set(MAPPING_CONTEXT, mappingContext.toJson(schemaVersion, thePredicate), predicate);
        }

        return jsonObjectBuilder.build();
    }

    @Override
    public JsonObject toJson(final JsonSchemaVersion schemaVersion, final JsonFieldSelector fieldSelector) {
        return toJson(schemaVersion, FieldType.notHidden()).get(fieldSelector);
    }

    @Override
    public boolean equals(final Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof ConnectionData)) {
            return false;
        }
        final ConnectionData that = (ConnectionData) o;
        return Objects.equals(connection, that.connection) &&
                connectionStatus == that.connectionStatus &&
                Objects.equals(mappingContext, that.mappingContext);
    }

    @Override
    public int hashCode() {
        return Objects.hash(connection, connectionStatus, mappingContext);
    }

    @Override
    public String toString() {
        return getClass().getSimpleName() + " [" +
                "connection=" + connection +
                ", connectionStatus=" + connectionStatus +
                ", mappingContext=" + mappingContext +
                "]";
    }
}
