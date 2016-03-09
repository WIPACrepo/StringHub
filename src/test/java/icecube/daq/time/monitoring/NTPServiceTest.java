package icecube.daq.time.monitoring;

import org.apache.log4j.BasicConfigurator;
import org.apache.log4j.Level;
import org.apache.log4j.Logger;
import org.apache.log4j.varia.NullAppender;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Test;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.TimeZone;

import static org.junit.Assert.*;

/**
 * Tests NTPService.java
 */
public class NTPServiceTest
{

    @BeforeClass
    public static void setupLogging()
    {
        // exercise logging calls, but output to nowhere
        BasicConfigurator.resetConfiguration();
        BasicConfigurator.configure(new NullAppender());
        Logger.getRootLogger().setLevel(Level.ALL);
    }

    @AfterClass
    public static void tearDownLogging()
    {
        BasicConfigurator.resetConfiguration();
    }

    /**
     * Test detection of last day of month.
     */
    @Test
    public void testLastDayOfMonthDetection()
    {
        /** not last day */
        Date[] notLastDays =
                {
                        new Date(1326598035000L), // JAN 15, 3:27:15 2012
                        new Date(1329276435000L), // FEB 15, 3:27:15 2012
                        new Date(1331782035000L), // MAR 15, 3:27:15 2012
                        new Date(1334460435000L), // APR 15, 3:27:15 2012
                        new Date(1337052435000L), // MAY 15, 3:27:15 2012
                        new Date(1338521235000L), // JUN 01, 3:27:15 2012
                        new Date(1342322835000L), // JUL 15, 3:27:15 2012
                        new Date(1345001235000L), // AUG 15, 3:27:15 2012
                        new Date(1347679635000L), // SEP 15, 3:27:15 2012
                        new Date(1350271635000L), // OCT 15, 3:27:15 2012
                        new Date(1352950035000L), // NOV 15, 3:27:15 2012
                        new Date(1356838035000L)  // DEC 30, 3:27:15 2012
                };

        /** last day of month */
        Date[] areLastDays =
                {
                        new Date(1327980435000L), // JAN 31, 3:27:15 2012
                        new Date(1330486035000L), // FEB 29, 3:27:15 2012
                        new Date(1333164435000L), // MAR 31, 3:27:15 2012
                        new Date(1335756435000L), // APR 30, 3:27:15 2012
                        new Date(1338434835000L), // MAY 31, 3:27:15 2012
                        new Date(1341026835000L), // JUN 30, 3:27:15 2012
                        new Date(1343705235000L), // JUL 31, 3:27:15 2012
                        new Date(1346383635000L), // AUG 31, 3:27:15 2012
                        new Date(1348975635000L), // SEP 30, 3:27:15 2012
                        new Date(1351654035000L), // OCT 31, 3:27:15 2012
                        new Date(1354246035000L), // NOV 30, 3:27:15 2012
                        new Date(1356924435000L)  // DEC 31, 3:27:15 2012
                };

        // formatter for UTC time strings
        final String ISO_FORMAT = "yyyy-MM-dd'T'HH:mm:ss.SSS zzz";
        final SimpleDateFormat format = new SimpleDateFormat(ISO_FORMAT);
        final TimeZone utc = TimeZone.getTimeZone("UTC");
        format.setTimeZone(utc);


        for (final Date day : notLastDays)
        {
            boolean answer =
                    NTPService.UtilityMethods.isLastDayOfMonth(day);
            assertFalse(format.format(day) + " is not the last day of the month",
                    answer);
        }

        for (final Date day : areLastDays)
        {
            boolean answer =
                    NTPService.UtilityMethods.isLastDayOfMonth(day);
            assertTrue(format.format(day) + " is the last day of the month",
                    answer);
        }
    }

    /**
     * Test calculating the number of seconds until midnight.
     */
    @Test
    public void testMillisToMidnight()
    {
        long[][] datesAndAnswers
                =
                {
                        {1459043061000L,80139000}, // MAR 27, 1:44:21 2016
                        {1459057919000L,65281000}, // MAR 27, 5:51:59 2016
                        {1459065600000L,57600000}, // MAR 27, 8:00:00 2016
                        {1459077479000L,45721000}, // MAR 27, 11:17:59 2016
                        {1459092033000L,31167000}, // MAR 27, 15:20:33 2016
                        {1459119633000L,3567000}, // MAR 27, 23:00:33 2016
                        {1459123199000L,1000}, // MAR 27, 23:59:59 2016
                };


        for(long[] pair : datesAndAnswers)
        {
            Date time = new Date(pair[0]);
            long answer = NTPService.UtilityMethods.millisUntilMidnight(time);
            assertEquals(pair[1], answer);
        }


    }

}
