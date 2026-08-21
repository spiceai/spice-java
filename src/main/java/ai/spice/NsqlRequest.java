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

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import com.google.gson.annotations.SerializedName;

/**
 * A natural-language query against the runtime's {@code /v1/nsql} endpoint.
 *
 * <p>
 * Only {@code query} is required. The runtime needs an LLM model configured
 * in the Spicepod to translate it; when exactly one is configured, {@code
 * model} may be left unset and the runtime selects it.
 */
public class NsqlRequest {
    @SerializedName("query")
    private final String query;

    @SerializedName("model")
    private String model;

    @SerializedName("datasets")
    private List<String> datasets;

    // Boxed and left null when unset, rather than a primitive boolean, so Gson
    // omits this field entirely instead of always sending "sample_data_enabled":
    // false — the runtime already defaults it to false, and gospice's equivalent
    // field is "omitempty".
    @SerializedName("sample_data_enabled")
    private Boolean sampleDataEnabled;

    @SerializedName("prompt_cache_key")
    private String promptCacheKey;

    /**
     * Creates a request for the given natural-language query.
     *
     * @param query the question to answer, in natural language
     */
    public NsqlRequest(String query) {
        this.query = query;
    }

    /**
     * Names the LLM used to generate SQL. When unset, the runtime uses the
     * only compatible model configured in the Spicepod, and reports an error
     * if there is not exactly one.
     *
     * @param model the model name
     * @return this request
     */
    public NsqlRequest withModel(String model) {
        this.model = model;
        return this;
    }

    /**
     * Hints which datasets to sample when building model context. This is a
     * sampling hint only — it does not restrict which tables the generated
     * query may reference. When unset, all datasets are used.
     *
     * @param datasets the dataset names to sample
     * @return this request
     */
    public NsqlRequest withDatasets(List<String> datasets) {
        this.datasets = datasets == null ? null : new ArrayList<>(datasets);
        return this;
    }

    /**
     * Includes sample rows in the context given to the model. It improves
     * generation on ambiguous schemas at the cost of sending data values to
     * the model.
     *
     * @param sampleDataEnabled whether to include sample data
     * @return this request
     */
    public NsqlRequest withSampleDataEnabled(boolean sampleDataEnabled) {
        this.sampleDataEnabled = sampleDataEnabled;
        return this;
    }

    /**
     * A stable key forwarded to the model provider for prompt caching. Reuse
     * it across related requests to benefit from it.
     *
     * @param promptCacheKey the cache key
     * @return this request
     */
    public NsqlRequest withPromptCacheKey(String promptCacheKey) {
        this.promptCacheKey = promptCacheKey;
        return this;
    }

    public String getQuery() {
        return this.query;
    }

    public String getModel() {
        return this.model;
    }

    public List<String> getDatasets() {
        return this.datasets == null ? null : Collections.unmodifiableList(this.datasets);
    }

    public boolean isSampleDataEnabled() {
        return Boolean.TRUE.equals(this.sampleDataEnabled);
    }

    public String getPromptCacheKey() {
        return this.promptCacheKey;
    }
}
