/*
 *    Copyright 2017 OICR
 *
 *    Licensed under the Apache License, Version 2.0 (the "License");
 *    you may not use this file except in compliance with the License.
 *    You may obtain a copy of the License at
 *
 *        http://www.apache.org/licenses/LICENSE-2.0
 *
 *    Unless required by applicable law or agreed to in writing, software
 *    distributed under the License is distributed on an "AS IS" BASIS,
 *    WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *    See the License for the specific language governing permissions and
 *    limitations under the License.
 */
package io.dockstore.webservice.resources.rss;

import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.List;
import java.util.Locale;

public class RSSFeed {
    private RSSHeader header;
    private List<RSSEntry> entries;

    public void setHeader(RSSHeader header) {
        this.header = header;
    }

    public void setEntries(List entries) {
        this.entries = entries;
    }

    public RSSHeader getHeader() {
        return header;
    }

    public List<RSSEntry> getEntries() {
        return entries;
    }

    public static String formatDate(Calendar cal) {
        SimpleDateFormat sdf = new SimpleDateFormat(
            "EEE, dd MMM yyyy HH:mm:ss Z", Locale.US);
        return sdf.format(cal.getTime());
    }
}
