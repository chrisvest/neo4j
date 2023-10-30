/*
 * Copyright (c) "Neo4j"
 * Neo4j Sweden AB [https://neo4j.com]
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
 * along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */
package org.neo4j.internal.kernel.api.helpers;

import java.util.Map;
import org.neo4j.internal.kernel.api.TokenSet;
import org.neo4j.values.storable.Value;

class NodeData {
    final long id;
    private final int[] labels;
    final Map<Integer, Value> properties;

    NodeData(long id, int[] labels, Map<Integer, Value> properties) {
        this.id = id;
        this.labels = labels;
        this.properties = properties;
    }

    TokenSet labelSet() {
        return new TokenSet() {
            @Override
            public int numberOfTokens() {
                return labels.length;
            }

            @Override
            public int token(int offset) {
                return labels[offset];
            }

            @Override
            public boolean contains(int token) {
                for (int label : labels) {
                    if (label == token) {
                        return true;
                    }
                }
                return false;
            }

            @Override
            public int[] all() {
                return labels;
            }
        };
    }
}
