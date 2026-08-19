/*
Copyright 2026 The Spice.ai OSS Authors

Permission is hereby granted, free of charge, to any person obtaining a copy
of this software and associated documentation files (the "Software"), to deal
in the Software without restriction, including without limitation the rights
to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
copies of the Software, and to permit persons to whom the Software is
furnished to do so, subject to the following conditions:

The above copyright notice and this permission notice shall be included in all
copies or substantial portions of the Software.

THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
SOFTWARE.
*/

package ai.spice;

import java.util.Collections;
import java.util.List;
import java.util.Map;

import com.google.gson.annotations.SerializedName;

/**
 * A single document matched by {@link SpiceClient#search(SearchRequest)}.
 *
 * <p>
 * The runtime omits {@code primary_key}, {@code data}, and {@code metadata}
 * from a match that has none. Unlike that wire shape, the getters below
 * return an empty map rather than {@code null} in that case, so callers never
 * need a null check.
 */
public class SearchMatch {

    @SerializedName("dataset")
    private String dataset;

    @SerializedName("_score")
    private double score;

    @SerializedName("matches")
    private Map<String, List<Object>> matches;

    @SerializedName("primary_key")
    private Map<String, Object> primaryKey;

    @SerializedName("data")
    private Map<String, Object> data;

    @SerializedName("metadata")
    private Map<String, Object> metadata;

    /**
     * The dataset the match was found in.
     *
     * @return the dataset name
     */
    public String getDataset() {
        return this.dataset;
    }

    /**
     * The match's similarity to the query. Higher is more similar.
     *
     * @return the score
     */
    public double getScore() {
        return this.score;
    }

    /**
     * The matched values keyed by the column they came from. Each value is a
     * list because one column can contribute several chunks to a single
     * match.
     *
     * @return the matched values, or an empty map if the runtime returned
     *         none
     */
    public Map<String, List<Object>> getMatches() {
        return this.matches == null ? Collections.emptyMap() : this.matches;
    }

    /**
     * Identifies the matched row. Empty when the dataset declares no primary
     * key.
     *
     * @return the primary key columns, or an empty map if the dataset has
     *         none
     */
    public Map<String, Object> getPrimaryKey() {
        return this.primaryKey == null ? Collections.emptyMap() : this.primaryKey;
    }

    /**
     * Any {@code additionalColumns} that were requested. Empty when none were
     * requested.
     *
     * @return the additional column values, or an empty map if none were
     *         requested
     */
    public Map<String, Object> getData() {
        return this.data == null ? Collections.emptyMap() : this.data;
    }

    /**
     * Extra per-match metadata the runtime attached. Empty when it attached
     * none.
     *
     * @return the metadata, or an empty map if the runtime attached none
     */
    public Map<String, Object> getMetadata() {
        return this.metadata == null ? Collections.emptyMap() : this.metadata;
    }
}
