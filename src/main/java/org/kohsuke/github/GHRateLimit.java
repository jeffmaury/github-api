package org.kohsuke.github;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import org.apache.commons.lang3.StringUtils;

import javax.annotation.Nonnull;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.Date;
import java.util.Objects;
import java.util.logging.Logger;

import static java.util.logging.Level.FINEST;

/**
 * Rate limit.
 *
 * @author Kohsuke Kawaguchi
 */
public class GHRateLimit {

    /**
     * Remaining calls that can be made.
     *
     * @deprecated This value should never have been made public. Use {@link #getRemaining()}
     */
    @Deprecated
    public int remaining;

    /**
     * Allotted API call per hour.
     *
     * @deprecated This value should never have been made public.  Use {@link #getLimit()}
     */
    @Deprecated
    public int limit;

    /**
     * The time at which the current rate limit window resets in UTC epoch seconds.
     * NOTE: that means to
     *
     * @deprecated This value should never have been made public. Use {@link #getResetDate()}
     */
    @Deprecated
    public Date reset;

    /**
     * Remaining calls that can be made.
     */
    private final int remainingCount;

    /**
     * Allotted API call per hour.
     */
    private final int limitCount;

    /**
     * The time at which the current rate limit window resets in UTC epoch seconds.
     */
    private final long resetEpochSeconds;

    /**
     * EpochSeconds time (UTC) at which this instance was created.
     */
    private final long createdAtEpochSeconds = System.currentTimeMillis() / 1000;

    /**
     * The calculated time at which the rate limit will reset.
     * Recalculated if {@link #recalculateResetDate} is called.
     */
    @Nonnull
    private Date resetDate;

    /**
     * Gets a placeholder instance that can be used when we fail to get one from the server.
     *
     * @return a GHRateLimit
     */
    public static GHRateLimit getPlaceholder() {
        final long oneHour = 60L * 60L;
        // This placeholder limit does not expire for a while
        // This make it so that calling rateLimit() multiple times does not result in multiple request
        GHRateLimit r = new GHRateLimit(1000000, 1000000, System.currentTimeMillis() / 1000L + oneHour);
        return r;
    }

    @JsonCreator
    public GHRateLimit(@JsonProperty("limit") int limit,
                @JsonProperty("remaining") int remaining,
                @JsonProperty("reset")long resetEpochSeconds) {
        this(limit, remaining, resetEpochSeconds, null);
    }

    @SuppressFBWarnings(value = "URF_UNREAD_PUBLIC_OR_PROTECTED_FIELD",
        justification = "Deprecated")
    public GHRateLimit(int limit, int remaining, long resetEpochSeconds, String updatedAt) {
        this.limitCount = limit;
        this.remainingCount = remaining;
        this.resetEpochSeconds = resetEpochSeconds;
        this.resetDate = recalculateResetDate(updatedAt);

        // Deprecated fields
        this.remaining = remaining;
        this.limit = limit;
        this.reset = new Date(resetEpochSeconds);
    }

    /**
     *
     * @param updatedAt a string date in RFC 1123
     * @return reset date based on the passed date
     */
    Date recalculateResetDate(String updatedAt) {
        long updatedAtEpochSeconds = createdAtEpochSeconds;
        if (!StringUtils.isBlank(updatedAt)) {
            try {
                // Get the server date and reset data, will always return a time in GMT
                updatedAtEpochSeconds = ZonedDateTime.parse(updatedAt, DateTimeFormatter.RFC_1123_DATE_TIME).toEpochSecond();
            } catch (DateTimeParseException e) {
                if (LOGGER.isLoggable(FINEST)) {
                    LOGGER.log(FINEST, "Malformed Date header value " + updatedAt, e);
                }
            }
        }

        // This may seem odd but it results in an accurate or slightly pessimistic reset date
        // based on system time rather than on the system being in sync with the server
        long calculatedSecondsUntilReset = resetEpochSeconds - updatedAtEpochSeconds;
        return resetDate = new Date((createdAtEpochSeconds + calculatedSecondsUntilReset) * 1000);
    }

    /**
     * Gets the remaining number of requests allowed before this connection will be throttled.
     *
     * @return an integer
     */
    public int getRemaining() {
        return remainingCount;
    }

    /**
     * Gets the total number of API calls per hour allotted for this connection.
     *
     * @return an integer
     */
    public int getLimit() {
        return limitCount;
    }

    /**
     * Gets the time in epoch seconds when the rate limit will reset.
     *
     * @return a long
     */
    public long getResetEpochSeconds() {
        return resetEpochSeconds;
    }

    /**
     * Whether the rate limit reset date indicated by this instance is in the
     *
     * @return true if the rate limit reset date has passed. Otherwise false.
     */
    public boolean isExpired() {
        return getResetDate().getTime() < System.currentTimeMillis();
    }

    /**
     * Returns the date at which the rate limit will reset.
     *
     * @return the calculated date at which the rate limit has or will reset.
     */
    @Nonnull
    public Date getResetDate() {
        return new Date(resetDate.getTime());
    }

    @Override
    public String toString() {
        return "GHRateLimit{" +
                "remaining=" + getRemaining() +
                ", limit=" + getLimit() +
                ", resetDate=" + getResetDate() +
                '}';
    }


    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        GHRateLimit rateLimit = (GHRateLimit) o;
        return getRemaining() == rateLimit.getRemaining() &&
            getLimit() == rateLimit.getLimit() &&
            getResetEpochSeconds() == rateLimit.getResetEpochSeconds() &&
            getResetDate().equals(rateLimit.getResetDate());
    }

    @Override
    public int hashCode() {
        return Objects.hash(getRemaining(), getLimit(), getResetEpochSeconds(), getResetDate());
    }

    private static final Logger LOGGER = Logger.getLogger(Requester.class.getName());
}
