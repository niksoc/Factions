/*
 * Copyright 2017 MovingBlocks
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.terasology.factions.utils;

import java.util.Objects;

public class OrderedPair<L, R> extends Pair<L, R> {

    public OrderedPair(L left, R right) {
        super(left, right);
    }

    @Override
    public int hashCode() {
        long l = left.hashCode() * 2654435761L;
        return (int) l + (int) (l >>> 32) + right.hashCode();
    }

    @Override
    public boolean equals(Object o) {
        if (!(o instanceof Pair)) return false;
        Pair pairo = (Pair) o;

        return Objects.equals(this.left, pairo.getLeft())
                && Objects.equals(this.right, pairo.getRight());
    }
}

