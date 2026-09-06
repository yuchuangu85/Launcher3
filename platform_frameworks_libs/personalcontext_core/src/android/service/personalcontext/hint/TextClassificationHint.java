/*
 * Copyright 2025 The Android Open Source Project
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
package android.service.personalcontext.hint;

import static java.util.Objects.requireNonNull;

import android.annotation.FlaggedApi;
import android.annotation.SystemApi;
import android.os.Bundle;
import android.service.personalcontext.Flags;
import android.service.personalcontext.Token;
import android.text.TextUtils;
import android.view.textclassifier.TextClassification;

import androidx.annotation.NonNull;

import java.util.Objects;

/**
 * A hint that contains a text classification request from selected text.
 * @hide
 */
@SystemApi
@FlaggedApi(Flags.FLAG_ENABLE_PERSONAL_CONTEXT_SERVICE)
public class TextClassificationHint extends ContextHint {
    @NonNull private final TextClassification.Request mTextClassificationRequest;
    @NonNull private final String mTextClassificationSessionId;

    static final String KEY_TEXT_CLASSIFICATION_REQUEST = "key_text_classification_request";
    static final String KEY_TEXT_CLASSIFICATION_SESSION_ID = "key_text_classification_session_id";

    /**
     * Creates a new {@link TextClassificationHint}
     *
     * @hide
     */
    TextClassificationHint(
            @NonNull ConstructorParams baseParams,
            @NonNull TextClassification.Request textClassificationRequest,
            @NonNull String textClassificationSessionId) {
        super(baseParams);
        mTextClassificationRequest = textClassificationRequest;
        mTextClassificationSessionId = textClassificationSessionId;
    }

    /**
     * Internal constructor only for use by {@link ContextHint#createHintFromBundle(Bundle)}.
     *
     * @hide
     */
    TextClassificationHint(@NonNull ConstructorParams baseParams, @NonNull Bundle bundle) {
        super(baseParams);
        mTextClassificationSessionId =
                requireNonNull(
                        bundle.getString(KEY_TEXT_CLASSIFICATION_SESSION_ID),
                        "Bundle must contain classification session id");
        mTextClassificationRequest =
                requireNonNull(
                        bundle.getParcelable(
                                KEY_TEXT_CLASSIFICATION_REQUEST, TextClassification.Request.class),
                        "Bundle must contain classification request");
    }

    /** @hide */
    @Override
    @HintType
    public int getHintType() {
        return HINT_TYPE_TEXT_CLASSIFICATION;
    }

    @NonNull
    @Override
    Bundle toBundleImpl() {
        Bundle bundle = new Bundle();
        bundle.putParcelable(KEY_TEXT_CLASSIFICATION_REQUEST, mTextClassificationRequest);
        bundle.putString(KEY_TEXT_CLASSIFICATION_SESSION_ID, mTextClassificationSessionId);
        return bundle;
    }

    @Override
    public boolean equals(Object o) {
        if (!(o instanceof TextClassificationHint that)) return false;
        return super.equals(o)
                && Objects.deepEquals(
                        this.mTextClassificationRequest, that.mTextClassificationRequest)
                && TextUtils.equals(
                        this.mTextClassificationSessionId, that.mTextClassificationSessionId);
    }

    @Override
    public int hashCode() {
        return Objects.hash(mTextClassificationRequest, mTextClassificationSessionId);
    }

    /** Returns the {@link TextClassification.Request} contained in this hint. */
    @NonNull
    public TextClassification.Request getTextClassificationRequest() {
        return mTextClassificationRequest;
    }

    /**
     * Returns the TextClassification sessionId. This is used by {@link
     * TextClassificationManagerService} and {@link PersonalContextManagerService} to trace the session
     * that the {@link TextClassification.Request} originated from.
     *
     * <p>If the session is destroyed by {@link TextClassificationService} before this hint is
     * processed, {@link TextClassificationManagerService} will ignore any insights generated from
     * this hint.
     */
    @NonNull
    public String getSessionId() {
        return mTextClassificationSessionId;
    }

    /** Builder used to create a {@link TextClassificationHint}. */
    @FlaggedApi(Flags.FLAG_ENABLE_PERSONAL_CONTEXT_SERVICE)
    public static final class Builder {
        private final ConstructorParams.Builder mBaseBuilder = new ConstructorParams.Builder();
        private final TextClassification.Request mTextClassificationRequest;
        private final String mTextClassificationSessionId;

        /**
         * Creates an instance of {@link TextClassificationHint.Builder} with the {@link
         * TextClassification.Request} contained in the hint.
         */
        public Builder(
                @NonNull TextClassification.Request textClassificationRequest,
                @NonNull String textClassificationSessionId) {
            mTextClassificationRequest =
                    requireNonNull(
                            textClassificationRequest,
                            "TextClassification request must be provided");
            mTextClassificationSessionId =
                    requireNonNull(
                            textClassificationSessionId,
                            "TextClassification session id must be provided");
        }

        /**
         * Adds a token to the resulting {@link TextClassificationHint}.
         *
         * @param token the token to add
         */
        @NonNull
        public Builder addToken(@NonNull Token token) {
            mBaseBuilder.addToken(token);
            return this;
        }

        /** Returns the built {@link TextClassificationHint}. */
        @NonNull
        public TextClassificationHint build() {
            return new TextClassificationHint(
                    mBaseBuilder.build(), mTextClassificationRequest, mTextClassificationSessionId);
        }
    }
}
