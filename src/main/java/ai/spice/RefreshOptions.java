/*
Copyright 2024 The Spice.ai OSS Authors

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

import com.google.gson.annotations.SerializedName;

public class RefreshOptions {
    @SerializedName("refresh_sql")
    public String refreshSql;
    @SerializedName("refresh_mode")
    public String refreshMode;
    @SerializedName("refresh_jitter_max")
    public String refreshJitterMax;

    public RefreshOptions withRefreshSql(String refreshSql) {
        this.refreshSql = refreshSql;
        return this;
    }

    public RefreshOptions withRefreshMode(String refreshMode) {
        this.refreshMode = refreshMode;
        return this;
    }

    public RefreshOptions withRefreshJitterMax(String refreshJitterMax) {
        this.refreshJitterMax = refreshJitterMax;
        return this;
    }
}