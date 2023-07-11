/*
 * Copyright (c) "Neo4j"
 * Neo4j Sweden AB [http://neo4j.com]
 *
 * This file is part of Neo4j.
 *
 * Neo4j is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */
package org.neo4j.values;

import static java.lang.String.format;

import java.util.UUID;
import org.neo4j.common.EntityType;

public abstract class ElementIdMapper {

    protected byte ELEMENT_ID_FORMAT_VERSION = 1;

    protected record ElementId(UUID databaseId, long entityId, EntityType entityType) {}

    protected ElementId decode(String id, EntityType expectedType) {
        try {
            var parts = readParts(id);
            var header = Byte.parseByte(parts[0]);
            verifyVersion(id, header);

            var databaseId = UUID.fromString(parts[1]);
            var entityId = Long.parseLong(parts[2]);
            var entityType = decodeEntityType(id, header, expectedType);
            return new ElementId(databaseId, entityId, entityType);
        } catch (IllegalArgumentException iae) {
            throw iae;
        } catch (Exception e) {
            throw new IllegalArgumentException(format("Element ID %s has an unexpected format.", id), e);
        }
    }

    private String[] readParts(String id) {
        String[] parts = id.split(":");
        if (parts.length != 3) {
            throw new IllegalArgumentException(format("Element ID %s has an unexpected format.", id));
        }
        return parts;
    }

    private void verifyVersion(String id, byte header) {
        byte version = (byte) (header >>> 2);
        if (version != ELEMENT_ID_FORMAT_VERSION) {
            throw new IllegalArgumentException(format("Element ID %s has an unexpected version %d", id, version));
        }
    }

    private EntityType decodeEntityType(String id, byte header, EntityType expectedType) {
        byte entityTypeId = (byte) (header & 0x3);
        var actualType =
                switch (entityTypeId) {
                    case 0 -> EntityType.NODE;
                    case 1 -> EntityType.RELATIONSHIP;
                    default -> throw new IllegalArgumentException(
                            format("Element ID %s has unknown entity type ID %s", id, entityTypeId));
                };
        verifyEntityType(id, actualType, expectedType);

        return actualType;
    }

    private void verifyEntityType(String id, EntityType actual, EntityType expected) {
        if (actual != expected) {
            throw new IllegalArgumentException(
                    format("Element ID %s has unexpected entity type %s, was expecting %s", id, actual, expected));
        }
    }

    public abstract String nodeElementId(long nodeId);

    public abstract long nodeId(String id);

    public abstract String relationshipElementId(long relationshipId);

    public abstract long relationshipId(String id);
}
