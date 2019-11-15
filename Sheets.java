
// import java.io.BufferedWriter;
// import java.io.FileWriter;
import java.util.Scanner;
import java.util.HashMap;
import java.util.Arrays;
import java.util.ArrayList;

import com.itextpdf.kernel.colors.Color;
import com.itextpdf.layout.borders.DoubleBorder;
import com.itextpdf.layout.property.VerticalAlignment;

import com.itextpdf.layout.Document;
import com.itextpdf.kernel.pdf.PdfWriter;
import com.itextpdf.kernel.pdf.PdfDocument;
import java.io.FileNotFoundException;

enum VoteCount {
    UNDER_VOTE, LEGAL_VOTE, OVER_VOTE;
}

public class Sheets {
    final int BALLOTS_PER_PAGE = 50;
    final int CONTESTS_PER_PAGE = 3;
    final String title; // Title of CVR
    final String[] column_titles; // titles of the columns
    // the first row is the candidates and second is their parties
    final String[] candidates;
    final String[] parties;
    final int cols; // number of total columns
    final int fc; // index of first contest in column_titles
    final int imprintedID_i; // index of "ImprintedID column"
    // true at ith index if ith column begins a new contest
    final boolean[] is_new_contest;
    // votes allowed for each contest, with key = index of column_titles
    // of the first column in the contest, val = votes allowed in the contest
    private final HashMap<Integer, Integer> votes_allowed;
    // all votes, each line corresponding to a single voter, with the first
    // cols - fc corresponding to info about each ballot from the CVR
    final String[][] vote_matrix;
    final int num_pages; // number of pages

    public Sheets(String title, String[] column_titles, String[] candidates, String[] parties, String[][] vote_matrix) {
        this.title = title;
        this.column_titles = column_titles;
        this.cols = column_titles.length;
        this.candidates = candidates;
        this.parties = parties;
        this.fc = getIndexOfFirstContest(column_titles);
        this.imprintedID_i = getIndexOfImprintedID(column_titles);
        this.is_new_contest = getContestStarts();
        this.votes_allowed = parseVotesPerContest(is_new_contest);
        this.vote_matrix = vote_matrix;
        this.num_pages = (vote_matrix.length + BALLOTS_PER_PAGE - 1) / BALLOTS_PER_PAGE; // Round up
    }

    /*
     * Return an array where starts[i] == true if and only if the ith column is the
     * first column in the contest.
     * 
     * An important invariant is that "BallotType" is always the column preceeding
     * the first contest.
     */
    public boolean[] getContestStarts() {
        boolean[] starts = new boolean[cols];
        int i;
        for (i = 0; i < fc; i++) {
            starts[i] = false;
        }
        for (i = fc; i < cols; i++) {
            starts[i] = !column_titles[i - 1].equals(column_titles[i]);
        }
        return starts;
    }

    private static int getIndexOfImprintedID(String[] column_titles) {
        for (int i = 0; i < column_titles.length; i++) {
            if (column_titles[i].equals("ImprintedId"))
                return i;
        }
        throw new IllegalArgumentException("CVR does not have a 'ImprintedId' Column");
    }

    private static int getIndexOfFirstContest(String[] column_titles) {
        for (int i = 0; i < column_titles.length; i++) {
            if (column_titles[i].equals("BallotType"))
                return i + 1;
        }
        throw new IllegalArgumentException("CVR does not have a 'BallotType' Column");
    }

    public HashMap<Integer, Integer> parseVotesPerContest(boolean[] is_new_contest) {
        HashMap<Integer, Integer> votesAllowed = new HashMap<Integer, Integer>();
        for (int i = 0; i < cols; i++) {
            if (is_new_contest[i]) {
                String[] tmp = column_titles[i].split(" \\(Vote For=");
                column_titles[i] = tmp[0];
                tmp = tmp[1].split("\\)");
                votesAllowed.put(i, Integer.parseInt(tmp[0]));
            }
        }
        return votesAllowed;
    }

    private int[] buildRunningSums(int[] prev_running_sums, int[] partial_sums) {
        int[] new_running_sums = new int[partial_sums.length];
        for (int i = 0; i < partial_sums.length; i++) {
            new_running_sums[i] = prev_running_sums[i] + partial_sums[i];
        }
        return new_running_sums;
    }

    private int[] buildPartialSums(int start_line) {
        int[] partial_sums = new int[cols - fc];
        for (int i = start_line; i < BALLOTS_PER_PAGE + start_line && i < vote_matrix.length; i++) {
            for (int j = fc; j < cols; j++) {
                if (vote_matrix[i][j] == null || vote_matrix[i][j].equals(""))
                    continue;
                partial_sums[j - fc] += Integer.parseInt(vote_matrix[i][j]);
            }
        }
        return partial_sums;
    }

    private VoteCount getVoteCount(int votes_expected, int votes) {
        if (votes < votes_expected) {
            return VoteCount.UNDER_VOTE;
        } else if (votes == votes_expected) {
            return VoteCount.LEGAL_VOTE;
        }
        return VoteCount.OVER_VOTE;
    }

    private VoteCount[][] markVoteCounts(int start_index) {
        VoteCount[][] vote_counts = new VoteCount[BALLOTS_PER_PAGE][cols];
        for (int i = start_index; i < start_index + BALLOTS_PER_PAGE && i < vote_matrix.length; i++) {
            int count = 0;
            int prev_new_contest_i = fc;
            for (int j = fc; j < cols; j++) {
                if (is_new_contest[j]) {
                    int votes_expected = votes_allowed.get(prev_new_contest_i);
                    VoteCount vc = getVoteCount(votes_expected, count);
                    for (int k = prev_new_contest_i; k < j; k++)
                        vote_counts[i - start_index][k] = vc;
                    count = 0;
                    prev_new_contest_i = j;
                }
                if (vote_matrix[i][j] == null || vote_matrix[i][j].equals(""))
                    continue;
                count += Integer.parseInt(vote_matrix[i][j]);
            }
            int votes_expected = votes_allowed.get(prev_new_contest_i);
            VoteCount vc = getVoteCount(votes_expected, count);
            for (int k = prev_new_contest_i; k < cols; k++) {
                vote_counts[i - start_index][k] = vc;
            }
        }
        return vote_counts;
    }

    private Page[] intermediateSheets() {
        Page[] pages = new Page[num_pages];
        for (int i = 0, CVR_start_i = 0; i < pages.length; i++, CVR_start_i += BALLOTS_PER_PAGE) {
            VoteCount[][] vote_counts = markVoteCounts(CVR_start_i);
            int[] partial_sums = buildPartialSums(CVR_start_i);
            int[] running_sums = partial_sums;
            if (i != 0) {
                running_sums = buildRunningSums(pages[i - 1].getRunningSums(), partial_sums);
            }
            pages[i] = new Page(this, i + 1, CVR_start_i, vote_counts, partial_sums, running_sums);
        }
        return pages;
    }

    private static String[] prepareColumnTitles(String[] contests, String[] column_titles_line) {
        String[] column_titles = new String[Math.max(contests.length, column_titles_line.length)];

        for (int i = 0; i < column_titles_line.length; i++) {
            column_titles[i] = column_titles_line[i];
        }
        for (int i = getIndexOfFirstContest(column_titles); i < column_titles.length; i++) {
            column_titles[i] = contests[i];
        }
        return column_titles;
    }

    public static String[] splitAtComma(String s) {
        ArrayList<String> out = new ArrayList<String>();
        int start = 0;
        int end = s.indexOf(",");
        while (end < s.length() && end != -1) {
            if (start == end)
                out.add("0");
            out.add(s.substring(start, end));
            start = end + 1;
            end = s.indexOf(",", start);
        }
        String end_of_s = s.substring(start);
        if (end_of_s != null && end_of_s != "")
            out.add(end_of_s);
        return out.toArray(new String[out.size()]);
    }

    public void writePDF(Page[] pages) throws FileNotFoundException {
        PdfWriter writer = new PdfWriter(title + ".pdf");
        PdfDocument pdfdoc = new PdfDocument(writer);
        Document doc = new Document(pdfdoc);
        doc.setMargins(0, 0, 0, 0);
        for (int i = 0; i < pages.length; i++) {
            pdfdoc.addNewPage();
            pages[i].formatPDFPage(pdfdoc, doc);
        }
        // for (Page p : pages) {
        // pdfdoc.addNewPage();
        // p.formatPDFPage(pdfdoc, doc);
        // }
        doc.close();
        pdfdoc.close();
    }

    public static void main(String[] args) {
        Scanner sc = new Scanner(System.in);
        String title_line = sc.nextLine();
        String title = title_line.split(",")[0];
        String contests_line = sc.nextLine();
        String[] contests = contests_line.split(",");
        String[] candidates = sc.nextLine().split(",");
        String[] parties = sc.nextLine().split(",");
        String[] column_titles = prepareColumnTitles(contests, parties);
        ArrayList<String> ballot_lines = new ArrayList<String>();
        while (sc.hasNext()) {
            ballot_lines.add(sc.nextLine());
        }
        String[][] vote_matrix = new String[ballot_lines.size()][column_titles.length];
        for (int i = 0; i < ballot_lines.size(); i++) {
            vote_matrix[i] = splitAtComma(ballot_lines.get(i)); // .split(",");
        }
        Sheets s = new Sheets(title, column_titles, candidates, parties, vote_matrix);
        try {
            s.writePDF(s.intermediateSheets());
        } catch (Exception e) {
        }
        sc.close();
    }
}