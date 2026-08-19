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

import java.util.List;

import com.google.gson.annotations.SerializedName;

/**
 * A search against the runtime's {@code /v1/search} endpoint.
 *
 * <p>
 * Only {@code text} is required. Supplying {@code keywords} adds a lexical
 * pass, which the runtime combines with the vector scores into a single
 * hybrid ranking.
 */
public class SearchRequest {

    @SerializedName("text")
    private final String text;

    @SerializedName("datasets")
    private List<String> datasets;

    @SerializedName("limit")
    private Integer limit;

    @SerializedName("where")
    private String where;

    @SerializedName("additional_columns")
    private List<String> additionalColumns;

    @SerializedName("keywords")
    private List<String> keywords;

    /**
     * Creates a search request.
     *
     * @param text the text to find similar documents for
     */
    public SearchRequest(String text) {
        this.text = text;
    }

    /**
     * Restricts the search to the named datasets. When unset, the runtime
     * searches every searchable dataset.
     *
     * @param datasets the dataset names
     * @return this request
     */
    public SearchRequest withDatasets(List<String> datasets) {
        this.datasets = datasets;
        return this;
    }

    /**
     * Caps the number of matches returned per dataset.
     *
     * @param limit the maximum number of matches, must be greater than 0
     * @return this request
     */
    public SearchRequest withLimit(int limit) {
        this.limit = limit;
        return this;
    }

    /**
     * A SQL predicate filtering candidate rows, without the leading
     * {@code WHERE} — for example {@code "user_id = 42"}.
     *
     * @param where the predicate
     * @return this request
     */
    public SearchRequest withWhere(String where) {
        this.where = where;
        return this;
    }

    /**
     * Names extra columns to return with each match. A primary key column is
     * returned in {@link SearchMatch#getPrimaryKey()}, the rest in
     * {@link SearchMatch#getData()}.
     *
     * @param additionalColumns the column names
     * @return this request
     */
    public SearchRequest withAdditionalColumns(List<String> additionalColumns) {
        this.additionalColumns = additionalColumns;
        return this;
    }

    /**
     * Drives the lexical pass of a hybrid search.
     *
     * @param keywords the keywords to match lexically
     * @return this request
     */
    public SearchRequest withKeywords(List<String> keywords) {
        this.keywords = keywords;
        return this;
    }

    public String getText() {
        return this.text;
    }

    public List<String> getDatasets() {
        return this.datasets;
    }

    public Integer getLimit() {
        return this.limit;
    }

    public String getWhere() {
        return this.where;
    }

    public List<String> getAdditionalColumns() {
        return this.additionalColumns;
    }

    public List<String> getKeywords() {
        return this.keywords;
    }
}
