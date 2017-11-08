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
package io.dockstore.provision;

import java.io.Console;
import java.math.BigDecimal;
import java.math.MathContext;
import java.math.RoundingMode;

import org.apache.commons.lang3.StringUtils;

/**
 * A printer of the progress for file provisoning plugins.
 *
 * @author dyuen
 */
public class ProgressPrinter {
    private static final int SIZE_OF_PROGRESS_BAR = 50;
    private static final int MAX_HEADER_LENGTH = 30;
    private static final int PROGRESS_INCREMENT = 1;
    private final int threads;
    private final String header;
    private boolean printedBefore = false;
    private int progress = 0;

    public ProgressPrinter() {
        this.threads = 1;
        this.header = "";
    }

    public ProgressPrinter(int threads, String header) {
        this.threads = threads;
        if (header.length() > MAX_HEADER_LENGTH * 2) {
            this.header = StringUtils.truncate(header, MAX_HEADER_LENGTH) + "..." + StringUtils.truncate(header, header.length() - MAX_HEADER_LENGTH, Integer.MAX_VALUE) + " ";
        } else {
            this.header = header;
        }
    }

    /**
     * Call to report on progress
     *
     * @param totalBytesTransferred bytes transferred so far
     * @param streamSize            total bytes to be transferred
     */
    public void handleProgress(long totalBytesTransferred, long streamSize) {

        BigDecimal numerator = BigDecimal.valueOf(totalBytesTransferred);
        BigDecimal denominator = BigDecimal.valueOf(streamSize);
        BigDecimal fraction = numerator.divide(denominator, new MathContext(2, RoundingMode.HALF_EVEN));
        BigDecimal percentage = fraction.movePointRight(2);

        if (percentage.intValue() == progress) {
            /* don't bother refreshing if no progress made */
            return;
        }

        BigDecimal outOfTwenty = fraction.multiply(new BigDecimal(SIZE_OF_PROGRESS_BAR));
        StringBuilder builder = new StringBuilder(header);
        if (threads == 1) {
            if (printedBefore) {
                builder.append('\r');
            }
        }

        builder.append("[");
        for (int i = 0; i < SIZE_OF_PROGRESS_BAR; i++) {
            if (i < outOfTwenty.intValue()) {
                builder.append("#");
            } else {
                builder.append(" ");
            }
        }

        builder.append("] ");
        builder.append(percentage.setScale(0, BigDecimal.ROUND_HALF_EVEN).toPlainString()).append("%");

        Console console = System.console();

        if (console != null || numerator.equals(denominator)) {
            if (threads == 1) {
                System.out.print(builder);
            } else {
                // do not refresh too often with multithreading
                if (percentage.intValue() < progress + PROGRESS_INCREMENT) {
                    return;
                }

                System.out.println(builder);
            }
        }
        // track progress
        printedBefore = true;
        progress = percentage.intValue();
    }
}
