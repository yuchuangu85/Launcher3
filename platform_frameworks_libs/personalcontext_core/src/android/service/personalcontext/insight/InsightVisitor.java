/*
 * Copyright (C) 2025 The Android Open Source Project
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

package android.service.personalcontext.insight;

import android.annotation.NonNull;

/**
 * A visitor for {@link ContextInsight} objects.
 *
 * <p>Implement this interface to define operations on different types of insights. Use the {@link
 * InsightTraverser} to walk the insight tree and apply the visitor. Because this interface is
 * stateless, implementations can be safely reused and shared.
 *
 * <p>The index argument is the order in which the insight was traversed during a pre-order
 * traversal.
 *
 * @hide
 */
public interface InsightVisitor {
    /** Visits an {@link InsightCollection}. */
    default void visit(@NonNull InsightCollection collection, int index) {}

    /** Visits an {@link ActionableInsight}. */
    default void visit(@NonNull ActionableInsight insight, int index) {}

    /** Visits a {@link DisplayInsight}. */
    default void visit(@NonNull DisplayInsight insight, int index) {}

    /** Visits a {@link BundleInsight}. */
    default void visit(@NonNull BundleInsight insight, int index) {}

    /** Visits a {@link HintInvalidationInsight}. */
    default void visit(@NonNull HintInvalidationInsight insight, int index) {}

    /** Visits an unknown {@link ContextInsight}. */
    default void visitUnknown(@NonNull ContextInsight insight, int index) {}
}
