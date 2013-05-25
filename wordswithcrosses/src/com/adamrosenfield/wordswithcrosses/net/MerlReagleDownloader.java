package com.adamrosenfield.wordswithcrosses.net;

import java.io.IOException;
import java.util.Calendar;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import com.adamrosenfield.wordswithcrosses.io.JPZIO;
import com.adamrosenfield.wordswithcrosses.puz.Puzzle;

/**
 * Merl Reagle's Crossword
 * URL: http://www.sundaycrosswords.com/
 * Date: Sunday
 */
public class MerlReagleDownloader extends AbstractJPZDownloader
{
    private static final String AUTHOR = "Merl Reagle";

    // Yes, there are two spaces there between the first two words
    private static final String DATE_REGEX = "For  puzzle of (\\d+)/(\\d+)/(\\d+), click";
    private static final Pattern DATE_PATTERN = Pattern.compile(DATE_REGEX);

    private static final String PUZZLE_REGEX = "<PARAM NAME=\"DATAFILE\" VALUE=\"([^\"]*)\">";
    private static final Pattern PUZZLE_PATTERN = Pattern.compile(PUZZLE_REGEX);

    private static final String TITLE_REGEX = ">&quot;(.*)&quot;<";
    private static final Pattern TITLE_PATTERN = Pattern.compile(TITLE_REGEX);

    private static final long MILLIS_PER_WEEK = (long)7*86400*1000;

    public MerlReagleDownloader()
    {
        super("", "Merl Reagle's Crossword");
    }

    public boolean isPuzzleAvailable(Calendar date)
    {
        // Only the most recent 4 puzzles are available
        return (date.get(Calendar.DAY_OF_WEEK) == Calendar.SUNDAY &&
                date.getTimeInMillis() > System.currentTimeMillis() - 4*MILLIS_PER_WEEK);
    }

    @Override
    public boolean download(Calendar date) throws IOException
    {
        // Figure out how many weeks old the given date is, rounded down
        Calendar now = Calendar.getInstance();
        long deltaMillis = now.getTimeInMillis() - date.getTimeInMillis();
        int weeksOld = (int)(deltaMillis / MILLIS_PER_WEEK);

        if (weeksOld >= 4 || weeksOld < 0)
        {
            return false;
        }

        // If the puzzle we want is 3 weeks old, check the 2-week-old puzzle
        // first, since AFAICT there's no way to get the date of the puzzle on
        // the 3-week-old page
        if (weeksOld == 3)
        {
            weeksOld--;
        }

        Calendar prevPuzzleDate = (Calendar)date.clone();
        prevPuzzleDate.add(Calendar.DATE, -7);

        String baseUrl = "http://www.sundaycrosswords.com/ccpuz/";

        // Try to scrape what we think the right page is.  If we're wrong,
        // figure out the correct page and then re-scrape that.
        for (int i = 0; i < 2; i++)
        {
            String scrapeUrl = baseUrl;
            if (weeksOld == 0)
            {
                scrapeUrl += "MPuz.php";
            }
            else
            {
                scrapeUrl += "MPuz" + weeksOld + "WO.php";
            }

            String scrapedPage = downloadUrlToString(scrapeUrl);

            // Check the date of the next puzzle on the page we scraped; if
            // we're scraping the 3-week-old page, assume we're right
            if (weeksOld < 3)
            {
                Matcher matcher = DATE_PATTERN.matcher(scrapedPage);
                if (!matcher.find())
                {
                    LOG.warning("Failed to scrape date in page: " + scrapeUrl);
                    return false;
                }

                // Get the day from the regex match
                String yearString = matcher.group(3);
                String monthString = matcher.group(1);
                String dayString = matcher.group(2);
                int year, month, day;
                try
                {
                    year = Integer.parseInt(yearString);
                    month = Integer.parseInt(monthString);
                    day = Integer.parseInt(dayString);
                }
                catch (NumberFormatException e)
                {
                    // This should never happen, since the regex group only
                    // matches digits
                    LOG.warning("Error parsing integer: " + matcher.group(0));
                    return false;
                }

                if (year != prevPuzzleDate.get(Calendar.YEAR) ||
                    month != prevPuzzleDate.get(Calendar.MONTH) + 1 ||
                    day != prevPuzzleDate.get(Calendar.DATE))
                {
                    // Date was wrong, try again
                    Calendar scrapedPrevPuzzleDate = Calendar.getInstance();
                    scrapedPrevPuzzleDate.set(year,  month,  day,  0,  0,  0);
                    deltaMillis = scrapedPrevPuzzleDate.getTimeInMillis() - prevPuzzleDate.getTimeInMillis();
                    int deltaWeeks = (int)(deltaMillis / MILLIS_PER_WEEK);
                    weeksOld += deltaWeeks;
                    LOG.info(
                        "Failed to scrape Merl Reagle, got puzzle with previous date of " +
                        year + "-" + month + "-" + day + ", expected previous date of " +
                        prevPuzzleDate);
                    continue;
                }
            }

            // Now that we got the right date (we hope), scrape the puzzle URL
            // and download it
            Matcher matcher = PUZZLE_PATTERN.matcher(scrapedPage);
            if (!matcher.find())
            {
                LOG.warning("Failed to find puzzle filename in page: " + scrapeUrl);
                return false;
            }

            String puzzleFilename = matcher.group(1);

            matcher = TITLE_PATTERN.matcher(scrapedPage);
            String title = (matcher.find() ? matcher.group(1) : puzzleFilename);
            MerlReagleMetadata metadataSetter = new MerlReagleMetadata(title, date.get(Calendar.YEAR));

            String url = baseUrl + puzzleFilename;
            return super.download(date, url, EMPTY_MAP, metadataSetter);
        }

        return false;
    }

	@Override
    protected String createUrlSuffix(Calendar date)
	{
	    return "";
	}

	private static class MerlReagleMetadata implements JPZIO.PuzzleMetadataSetter
	{
	    private String title;
	    private int year;

	    public MerlReagleMetadata(String title, int year)
	    {
	        this.title = title;
	        this.year = year;
	    }

        public void setMetadata(Puzzle puzzle)
        {
            puzzle.setAuthor(AUTHOR);
            puzzle.setTitle(title);
            puzzle.setCopyright("\u00A9 " + year + " " + AUTHOR);
        }
	}
}