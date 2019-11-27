
import java.lang.IllegalArgumentException;
import java.util.ArrayList;

class Contest {
    private final String title; // the title of the sheet
    private final int csi; // contests start index
    private final int cols; // columns in contest
    private final VotingSheets sheets; // contest sheet
    private final String contest_name; // name of the contest
    private final int width; // width of the contest in a table

    public Contest(VotingSheets sheets, String title, String contest_name, int contest_start_i, int cols, int width) {
        this.title = title;
        this.csi = contest_start_i;
        this.cols = cols;
        this.sheets = sheets;
        this.contest_name = contest_name;
        this.width = width;
    }

    public String title() {
        return title;
    }

    public int width() {
        return width;
    }

    public String contest_name() {
        return contest_name;
    }

    public int cols() {
        return cols;
    }

    public int ballots() {
        return sheets.ballots();
    }

    public ArrayList<Integer> buildPartialSums(int start_line) {
        ArrayList<Integer> partial_sums = new ArrayList<Integer>(cols);
        for (int j = 0; j < cols; j++) {
            partial_sums.add(0);
        }
        for (int i = start_line; i < sheets.BALLOTS_PER_PAGE() + start_line && i < ballots(); i++) {
            for (int j = 0; j < cols; j++) {
                String vote = getVote(j, i);
                if (vote.equals("-"))
                    continue;
                partial_sums.set(j, partial_sums.get(j) + Integer.parseInt(vote));
            }
        }
        return partial_sums;
    }

    public int BALLOTS_PER_PAGE() {
        return sheets.BALLOTS_PER_PAGE();
    }

    // get the ith candidate for this race
    public String candidate(int i) {
        if (i < 0 || i >= cols)
            throw new IllegalArgumentException("column not in this contest");
        return sheets.getCandidate(i + csi);
    }

    // get the ith party for this race
    public String party(int i) {
        if (i < 0 || i >= cols)
            throw new IllegalArgumentException("column not in this contest");
        return sheets.getParty(i + csi);
    }

    // get the row-th vote for the col-th candidate
    public String getVote(int col, int row) {
        if (col < 0 || col >= cols)
            throw new IllegalArgumentException("column not in this contest");
        String vote = this.sheets.getVote(row, col + csi);
        if (vote == null || vote.equals(""))
            return "-";
        return vote;
    }

    // get the VoteCount for the ith ballot
    public VoteCount getVoteCount(int i) {
        return sheets.getVoteCount(i, csi);
    }

    // get the imprintedID for the ith ballot
    public String getImprintedID(int i) {
        return sheets.getImprintedID(i);
    }
}