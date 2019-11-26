
import com.itextpdf.kernel.pdf.PdfWriter;
import com.itextpdf.kernel.pdf.PdfDocument;
import com.itextpdf.layout.Document;
import com.itextpdf.layout.element.AreaBreak;

import java.io.FileNotFoundException;
import java.lang.IllegalArgumentException;

class ContestSheets {
    private final String header; // the header on the top of the contest sheet
    private final int csi; // contests start index
    private final int cols; // columns in contest
    private final Sheets sheets; // contest sheet
    private final int num_pages; // number of pages
    private final String contest_name; // name of the contest

    public ContestSheets(Sheets sheets, String header, String contest_name, int contest_start_i, int cols) {
        this.header = header;
        this.csi = contest_start_i;
        this.cols = cols;
        this.sheets = sheets;
        this.num_pages = (ballots() + BALLOTS_PER_PAGE() - 1) / BALLOTS_PER_PAGE(); // Round up
        this.contest_name = contest_name;
    }

    public String header() {
        return header;
    }

    public int cols() {
        return cols;
    }

    public void printContestSheet() throws FileNotFoundException {
        String file_name = removeSlashes(sheets.title()) + "/" + removeSlashes(contest_name);
        PdfWriter writer = new PdfWriter(file_name + ".pdf");
        PdfDocument pdfdoc = new PdfDocument(writer);
        Document doc = new Document(pdfdoc);
        doc.setMargins(0, 0, 0, 0);
        int[] prev_running_sums = new int[cols];
        for (int i = 0, votes_start_i = 0; i < this.num_pages; i++, votes_start_i += BALLOTS_PER_PAGE()) {
            pdfdoc.addNewPage();
            int[] partial_sums = buildPartialSums(votes_start_i);
            Page p = new Page(this, i + 1, votes_start_i, partial_sums, prev_running_sums);
            p.formatPDFPage(pdfdoc, doc);
            updatePrevRunningSums(prev_running_sums, partial_sums);
            if (i != num_pages - 1)
                doc.add(new AreaBreak());
        }
        pdfdoc.removePage(num_pages + 1);
        doc.close();
        pdfdoc.close();
    }

    public int ballots() {
        return sheets.ballots();
    }

    private String removeSlashes(String s) {
        return s.replaceAll("/", "-");
    }

    private void updatePrevRunningSums(int[] prev_running_sums, int[] partial_sums) {
        for (int i = 0; i < partial_sums.length; i++) {
            prev_running_sums[i] += partial_sums[i];
        }
    }

    private int[] buildPartialSums(int start_line) {
        int[] partial_sums = new int[cols];
        for (int i = start_line; i < sheets.BALLOTS_PER_PAGE() + start_line && i < ballots(); i++) {
            for (int j = 0; j < cols; j++) {
                String vote = getVote(j, i);
                if (vote.equals("-"))
                    continue;
                partial_sums[j] += Integer.parseInt(vote);
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