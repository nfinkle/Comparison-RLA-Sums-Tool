
// import java.io.BufferedWriter;
// import java.io.FileWriter;
import java.util.Scanner;
import java.util.HashMap;
import java.util.Arrays;
import java.util.Comparator;
import java.util.ArrayList;

import java.io.File;

import com.itextpdf.kernel.pdf.PdfWriter;
import com.itextpdf.kernel.pdf.PdfDocument;
import com.itextpdf.kernel.geom.PageSize;

enum VoteCount {
    UNDER_VOTE, LEGAL_VOTE, OVER_VOTE;
}

public class VotingSheets {
    private final int BALLOTS_PER_PAGE = 50;
    private final String title; // Title of CVR
    private final String[] column_titles; // titles of the columns
    // the first row is the candidates and second is their parties
    private final String[] candidates;
    private final String[] parties;
    private final int cols; // number of total columns
    private final int fc; // index of first contest in column_titles
    private final int imprintedID_i; // index of "ImprintedID column"
    // true at ith index if ith column begins a new contest
    private final boolean[] is_new_contest;
    // votes allowed for each contest, with key = index of column_titles
    // of the first column in the contest, val = votes allowed in the contest
    private final HashMap<Integer, Integer> votes_allowed;
    // all votes, each line corresponding to a single voter, with the first
    // cols - fc corresponding to info about each ballot from the CVR
    private final String[][] vote_matrix;
    private final HashMap<Integer, Integer> contest_cols; // how many columns is each contests
    private final VoteCount[][] vote_counts; // vote_counts

    public VotingSheets(String title, String[] column_titles, String[] candidates, String[] parties,
            String[][] vote_matrix) {
        this.title = title;
        this.column_titles = column_titles;
        this.cols = column_titles.length;
        this.candidates = candidates;
        this.parties = extendParties(parties, candidates);
        this.fc = getIndexOfFirstContest(column_titles);
        this.imprintedID_i = getIndexOfImprintedID(column_titles);
        this.is_new_contest = getContestStarts();
        this.votes_allowed = parseVotesPerContest(is_new_contest);
        this.contest_cols = getContestColumns();
        this.vote_matrix = vote_matrix;
        Arrays.sort(this.vote_matrix, new ImprintedIDComparator());
        this.vote_counts = markVoteCounts();
    }

    private class ImprintedIDComparator implements Comparator<String[]> {
        public int compare(String[] first, String[] second) {
            String[] f = first[imprintedID_i].split("-");
            String[] s = second[imprintedID_i].split("-");
            for (int i = 0; i < f.length && i < s.length; i++) {
                if (!s[i].equals(f[i])) {
                    try {
                        int i_f = Integer.parseInt(f[i]);
                        int i_s = Integer.parseInt(s[i]);
                        return i_f - i_s;
                    } catch (NumberFormatException e) {
                        return s[i].compareTo(f[i]);
                    }
                }
            }
            return f.length - s.length;
        }
    }

    // get the ith candidate of the CVR
    public String getCandidate(int i) {
        if (i < fc || i >= cols)
            throw new IllegalArgumentException("column out of bounds");
        return candidates[i];
    }

    // get the party of the ith candidate
    public String getParty(int i) {
        if (i < fc || i >= cols)
            throw new IllegalArgumentException("column out of bounds");
        return parties[i];
    }

    public int BALLOTS_PER_PAGE() {
        return BALLOTS_PER_PAGE;
    }

    public String title() {
        return title;
    }

    public int ballots() {
        return vote_matrix.length;
    }

    // return the imprintedID of the ith ballot
    public String getImprintedID(int i) {
        return vote_matrix[i][imprintedID_i];
    }

    // get the VoteCount for the ith ballot
    public VoteCount getVoteCount(int row, int col) {
        return vote_counts[row][col];
    }

    public String getVote(int row, int col) {
        if (row < 0 || row >= ballots())
            throw new IllegalArgumentException("row out of bounds");
        if (col < 0 || col >= cols)
            throw new IllegalArgumentException("col out of bounds");
        return vote_matrix[row][col];
    }

    /*
     * Return an array where starts[i] == true if and only if the ith column is the
     * first column in the contest.
     * 
     * An important invariant is that "BallotType" is always the column preceeding
     * the first contest.
     */
    private boolean[] getContestStarts() {
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

    private static String[] extendParties(String[] parties, String[] candidates) {
        if (parties.length >= candidates.length)
            return parties;
        String[] new_parties = new String[candidates.length];
        for (int i = 0; i < parties.length; i++) {
            new_parties[i] = parties[i];
        }
        for (int i = parties.length; i < candidates.length; i++) {
            new_parties[i] = "";
        }
        return new_parties;
    }

    private HashMap<Integer, Integer> getContestColumns() {
        HashMap<Integer, Integer> contest_cols = new HashMap<Integer, Integer>();
        int prev = fc;
        for (int i = this.fc + 1; i < this.cols; i++) {
            if (is_new_contest[i]) {
                contest_cols.put(prev, i - prev);
                prev = i;
            }
        }
        contest_cols.put(prev, this.cols - prev);
        return contest_cols;
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

    private VoteCount calcVoteCount(int votes_expected, int votes) {
        if (votes < votes_expected) {
            return VoteCount.UNDER_VOTE;
        } else if (votes == votes_expected) {
            return VoteCount.LEGAL_VOTE;
        }
        return VoteCount.OVER_VOTE;
    }

    private VoteCount[][] markVoteCounts() {
        VoteCount[][] vote_counts = new VoteCount[this.vote_matrix.length][this.cols];
        for (int i = 0; i < this.vote_matrix.length; i++) {
            int count = 0;
            int prev_new_contest_i = fc;
            for (int j = fc; j < cols; j++) {
                if (is_new_contest[j]) {
                    int votes_expected = votes_allowed.get(prev_new_contest_i);
                    VoteCount vc = calcVoteCount(votes_expected, count);
                    for (int k = prev_new_contest_i; k < j; k++)
                        vote_counts[i][k] = vc;
                    count = 0;
                    prev_new_contest_i = j;
                }
                if (vote_matrix[i][j] == null || vote_matrix[i][j].equals(""))
                    continue;
                count += Integer.parseInt(vote_matrix[i][j]);
            }
            int votes_expected = votes_allowed.get(prev_new_contest_i);
            VoteCount vc = calcVoteCount(votes_expected, count);
            for (int k = prev_new_contest_i; k < cols; k++) {
                vote_counts[i][k] = vc;
            }
        }
        return vote_counts;
    }

    private static String splitToKLines(String s, int k, int len) {
        s = s.replaceAll(" ", "\n").replaceAll("\n/\n", " /\n").replaceAll("\n-\n", " -\n");
        for (int i = 0; i <= 9; i++) {
            String ofInt = Integer.toString(i);
            s = s.replaceAll("\n" + ofInt + "\n", " " + ofInt + "\n").replaceAll("\n" + ofInt + "$", " " + ofInt);
        }
        String[] words = s.split("\n");
        if (words.length <= k) {
            return s;
        }
        StringBuilder fullText = new StringBuilder();
        StringBuilder line = new StringBuilder();
        for (int i = 0; i < words.length; i++) {
            line.append(words[i]).append(" ");
            if (i + 1 == words.length || line.length() + words[i + 1].length() > len) {
                line.deleteCharAt(line.length() - 1);
                fullText.append(line.toString()).append("\n");
                line = new StringBuilder();
            }
        }
        return fullText.toString();
    }

    private String getContestName(String contest_name, int col, int cols) {
        int width = 2 * cols;
        for (int i = 0; i < cols; i++) {
            int cur = 0;
            for (String word : candidates[col + i].split(" ")) {
                cur = Math.max(cur, word.length());
            }
            for (String word : parties[col + i].split(" ")) {
                cur = Math.max(cur, word.length());
            }
            width += cur;
        }
        int i;
        for (i = 1; contest_name.length() / i > width; i++)
            ;
        while (i > 5) {
            width += 2;
            int j = 4;
            for (; contest_name.length() / j > width; j++)
                ;
            i = j;
        }
        return splitToKLines(contest_name, i, width);
    }

    private Contest[] makeContests() {
        int contests = 0;
        for (int i = 0; i < is_new_contest.length; i++) {
            if (is_new_contest[i])
                contests++;
        }
        Contest[] contest_sheets = new Contest[contests];
        for (int i = fc, col = 0; i < cols; i++) {
            if (is_new_contest[i]) {
                int cols = contest_cols.get(i);
                String contest_name = getContestName(column_titles[i], i, cols);
                int width = 0;
                for (String s : contest_name.split("\n")) {
                    width = Math.max(width, s.length());
                }
                contest_sheets[col++] = new Contest(this, title, contest_name, i, cols, width);
            }
        }
        return contest_sheets;
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
                out.add("");
            else
                out.add(s.substring(start, end));
            start = end + 1;
            end = s.indexOf(",", start);
        }
        String end_of_s = s.substring(start);
        if (end_of_s != null && end_of_s != "")
            out.add(end_of_s);
        return out.toArray(new String[out.size()]);
    }

    private static String removeSlashes(String s) {
        return s.replaceAll("/", "-");
    }

    public static void main(String[] args) {
        if (args.length != 1) {
            System.err.println("USAGE: java VotingSheets CVR_name");
            return;
        }
        if (!args[0].contains(".csv")) {
            System.err.println("The CVR must be of type CSV");
            return;
        }
        File in;
        Scanner scanner;
        try {
            in = new File(args[0]);
            scanner = new Scanner(in);
        } catch (Exception e) {
            System.err.println("No file found at " + args[0]);
            return;
        }
        String title_line = scanner.nextLine();
        String title = title_line.split(",")[0];
        String contests_line = scanner.nextLine();
        String[] contest_names = contests_line.split(",");
        String[] candidates = scanner.nextLine().split(",");
        String[] parties = scanner.nextLine().split(",");
        String[] column_titles = prepareColumnTitles(contest_names, parties);
        ArrayList<String> ballot_lines = new ArrayList<String>();
        while (scanner.hasNext()) {
            ballot_lines.add(scanner.nextLine());
        }
        String[][] vote_matrix = new String[ballot_lines.size()][column_titles.length];
        for (int i = 0; i < ballot_lines.size(); i++) {
            vote_matrix[i] = splitAtComma(ballot_lines.get(i)); // .split(",");
        }
        VotingSheets s = new VotingSheets(title, column_titles, candidates, parties, vote_matrix);
        File f = new File(title);
        f.mkdir();
        Contest[] contests = s.makeContests();
        for (int c = 0, files = 1; c < contests.length; c++) {
            try {
                String file_name = removeSlashes(s.title()) + "/" + Integer.toString(files++); // +
                // removeSlashes(contest_name);
                PdfWriter writer = new PdfWriter(file_name + ".pdf");
                PdfDocument pdfdoc = new PdfDocument(writer);
                PageSize ps = pdfdoc.getDefaultPageSize();
                int MAX_TABLE_WIDTH = (int) (ps.getWidth() - ps.getWidth() / 20) * 2 / 10;
                int start = c;
                int width = 2;
                while (width < MAX_TABLE_WIDTH && c < contests.length) {
                    if (width + contests[c].width() > MAX_TABLE_WIDTH) {
                        break;
                    }
                    width += contests[c].width() + 2;
                    c++;
                }
                SingleFile.writePDF(Arrays.copyOfRange(contests, start, c--), s, pdfdoc);
                writer.close();
                pdfdoc.close();
            } catch (Exception e) {
                System.err.println(contests[c].title() + ": ");
                e.printStackTrace();
                System.err.println();
            }
        }

        scanner.close();
    }
}