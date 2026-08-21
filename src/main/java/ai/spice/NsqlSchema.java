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

import com.google.gson.annotations.SerializedName;

/**
 * The schema of the rows an {@link SpiceClient#nsql(NsqlRequest)} call
 * returned.
 *
 * <p>
 * {@link #getFields()} is empty when the generated query returned no rows —
 * the runtime omits the schema body in that case.
 */
public class NsqlSchema {
    @SerializedName("fields")
    private List<NsqlField> fields;

    /**
     * The columns of the result, in order.
     *
     * @return the fields, or an empty list when the runtime omitted them
     */
    public List<NsqlField> getFields() {
        return this.fields == null ? Collections.emptyList() : this.fields;
    }
}
