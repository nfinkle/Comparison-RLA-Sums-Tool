
import com.itextpdf.kernel.colors.ColorConstants;
import com.itextpdf.kernel.geom.PageSize;
import com.itextpdf.kernel.pdf.PdfDocument;
import com.itextpdf.layout.Document;
import com.itextpdf.layout.borders.Border;
import com.itextpdf.layout.borders.SolidBorder;
import com.itextpdf.layout.element.Cell;
import com.itextpdf.layout.element.Paragraph;
import com.itextpdf.layout.element.Table;
import com.itextpdf.layout.property.HorizontalAlignment;
import com.itextpdf.layout.property.TextAlignment;

import java.awt.FontMetrics;

class Page {
    // ID of the page (which number)
    private final int pageID;
    // index to start at CVR_lines
    private final int votes_line_start_i;
    // stores whether each vote was an overvote, an undervote, or a legal vote
    private final VoteCount[][] vote_counts;
    // sums of the votes on each page
    private final int[] partial_sums;
    // running_sums[i] is the sum of all partial_sums[i] for Pages with smaller IDs
    private final int[] running_sums;

    /* Now information passed from Sheets */
    private final int BALLOTS_PER_PAGE;
    private final String title; // Title of CVR
    private final String[] column_titles; // titles of the columns
    // the first row is the candidates and second is their parties
    private final String[] candidates;
    final String[] parties;
    private final int cols; // number of total columns
    private final int fc; // index of first contest in column_titles
    private final int imprintedID_i; // index of "ImprintedID column"
    // true at ith index if ith column begins a new contest
    private final boolean[] is_new_contest;
    // all votes, each line corresponding to a single voter, with the first
    // cols - fc corresponding to info about each ballot from the CVR
    private final String[][] vote_matrix;
    private final int num_lines_on_page; // number of lines in table on the page

    public Page(Sheets sheets, int pageID, int votes_line_start_i, VoteCount[][] vote_counts, int[] partial_sums,
            int[] running_sums) {
        this.pageID = pageID;
        this.votes_line_start_i = votes_line_start_i;
        this.vote_counts = vote_counts;
        this.partial_sums = partial_sums;
        this.running_sums = running_sums;
        this.BALLOTS_PER_PAGE = sheets.BALLOTS_PER_PAGE;
        this.title = sheets.title;
        this.column_titles = sheets.column_titles;
        this.candidates = sheets.candidates;
        this.parties = sheets.parties;
        this.cols = sheets.cols;
        this.fc = sheets.fc;
        this.imprintedID_i = sheets.imprintedID_i;
        this.is_new_contest = sheets.is_new_contest;
        this.vote_matrix = sheets.vote_matrix;
        int lines = vote_matrix.length - votes_line_start_i;
        this.num_lines_on_page = lines > BALLOTS_PER_PAGE ? BALLOTS_PER_PAGE : lines;
    }

    public int[] getRunningSums() {
        return running_sums;
    }

    private Table createPageNumberTable(float left, float bottom, float fontSize) {
        Cell cell = new Cell();
        cell.add(new Paragraph("" + pageID));
        cell.setBorder(new SolidBorder(ColorConstants.BLACK, 1));
        Table table = new Table(1);
        table.addCell(cell);
        table.setPadding(0);
        table.setFixedPosition(pageID, left, bottom, cell.getWidth());
        table.setFontSize(fontSize);
        table.setTextAlignment(TextAlignment.LEFT);
        return table;
    }

    private void addPageNumbers(Document doc, float fontSize, PageSize ps) {
        float top = ps.getHeight() - 2 * fontSize;
        float bottom = 0;
        float left = 0;
        // float right = ps.getWidth() - textWidth * fontSize;
        doc.add(createPageNumberTable(left, top, fontSize));
        doc.add(createPageNumberTable(left, bottom, fontSize));
        // doc.add(createPageNumberTable(pdfdoc, right, top, TextAlignment.RIGHT,
        // fontSize));
        // doc.add(createPageNumberTable(pdfdoc, right, bottom, TextAlignment.RIGHT,
        // fontSize));
    }

    private void addTitle(Document doc, float fontSize, PageSize ps) {
        Table table = new Table(1);
        int len = this.title.length();
        table.setWidth(len);
        float middle_with_offset = ps.getWidth() / 2 - (fontSize * len / 2);
        float top_row = ps.getHeight() - 2 * fontSize;
        table.setFixedPosition(pageID, middle_with_offset, top_row, len * fontSize);
        table.setTextAlignment(TextAlignment.CENTER);
        Paragraph p = new Paragraph(this.title);
        p.setFontSize(fontSize);
        Cell cell = new Cell();
        cell.setBorder(Border.NO_BORDER);
        cell.add(p);
        table.addCell(cell);
        doc.add(table);
    }

    public float addTitlesToTable(Table table, float bottomBorderThickness) {
        float width = addColumnTitlesRow(table);
        table.startNewRow();
        addPartiesRow(table);
        table.startNewRow();
        addCandidateNamesRow(table, bottomBorderThickness);
        return width;
    }

    private float addColumnTitlesRow(Table table) {
        float width = 0;
        Cell imprintedID = new Cell();
        imprintedID.add(new Paragraph("imprintedID"));
        table.addCell(imprintedID);
        int cells_in_contest = 0;
        for (int i = column_titles.length - 1; i >= this.fc; i--) {
            if (this.is_new_contest[i]) {
                width += column_titles[i].length();
                Cell c = new Cell(1, cells_in_contest + 1);
                table.addCell(c.add(new Paragraph(this.column_titles[i])));
                cells_in_contest = 0;
            } else {
                cells_in_contest++;
            }
        }
        return width;
    }

    private void addPartiesRow(Table table) {
        table.addCell(new Cell()); // skip the imprintedID cell
        for (int i = this.fc; i < parties.length; i++) {
            Cell c = new Cell().add(new Paragraph(this.parties[i]));
            table.addCell(c);
        }
    }

    private void addCandidateNamesRow(Table table, float bottomBorderThickness) {
        Cell c = new Cell(); // skip the imprintedID cell
        c.setBorderBottom(new SolidBorder(bottomBorderThickness));
        table.addCell(c);
        for (int i = this.fc; i < candidates.length; i++) {
            c = new Cell().add(new Paragraph(this.candidates[i]));
            c.setBorderBottom(new SolidBorder(bottomBorderThickness));
            table.addCell(c);
        }
    }

    private void addVotesToTable(Table table) {
        for (int i = votes_line_start_i; i < votes_line_start_i + this.BALLOTS_PER_PAGE
                && i < this.vote_matrix.length; i++) {
            table.startNewRow();
            table.addCell(new Cell().add(new Paragraph(this.vote_matrix[i][this.imprintedID_i])));
            for (int j = this.fc; j < this.cols; j++) {
                int vote = 0;
                if (this.vote_matrix[i][j] != null && !this.vote_matrix[i][j].equals("")) {
                    vote = Integer.parseInt(this.vote_matrix[i][j]);
                }
                Cell c = new Cell().add(new Paragraph(Integer.toString(vote)));
                if (vote_counts[i - votes_line_start_i][j] == VoteCount.UNDER_VOTE) {
                    c.setBackgroundColor(ColorConstants.LIGHT_GRAY);
                } else if (vote_counts[i - votes_line_start_i][j] == VoteCount.OVER_VOTE) {
                    c.setBackgroundColor(ColorConstants.RED);
                }
                table.addCell(c);
            }
        }
    }

    private void addSumsToTable(Table table, float topBorderThickness) {
        Cell partial = new Cell().add(new Paragraph("Partial"));
        partial.setBorderTop(new SolidBorder(topBorderThickness));
        table.addCell(partial);
        for (int i = 0; i < partial_sums.length; i++) {
            Paragraph p = new Paragraph(Integer.toString(partial_sums[i]));
            Cell c = new Cell();
            c.setBorderTop(new SolidBorder(topBorderThickness));
            table.addCell(c.add(p));
        }
        table.startNewRow();
        Cell running = new Cell().add(new Paragraph("Running"));
        table.addCell(running);
        for (int i = 0; i < running_sums.length; i++) {
            Paragraph p = new Paragraph(Integer.toString(running_sums[i]));
            table.addCell(new Cell().add(p));
        }
    }

    public float addVotesTable(Document doc, PageSize ps, float fontSize) {
        Table table = new Table(this.vote_matrix.length + 1);
        float width = addTitlesToTable(table, 2) * (fontSize - 2);
        addVotesToTable(table);
        table.setFontSize(fontSize);
        table.startNewRow();
        addSumsToTable(table, 2);
        table.setHorizontalAlignment(HorizontalAlignment.CENTER);
        table.setFixedPosition(pageID, ps.getWidth() / 2 - width / 2, BALLOTS_PER_PAGE, width);
        table.setTextAlignment(TextAlignment.CENTER);
        doc.add(table);
        return width;
    }

    // private void addRunningSumsTable(Document doc, PageSize ps, float width,
    // float fontSize) {
    // Table table = new Table(this.vote_matrix.length + 1);
    // table.startNewRow();
    // table.setFontSize(fontSize);
    // Cell c = new Cell().add(new Paragraph("Running"));
    // table.addCell(c);
    // for (int i = 0; i < running_sums.length; i++) {
    // Paragraph p = new Paragraph(Integer.toString(running_sums[i]));
    // table.addCell(new Cell().add(p));
    // }
    // table.setHorizontalAlignment(HorizontalAlignment.CENTER);
    // table.setFixedPosition(pageID, ps.getWidth() / 2 - width / 2, 25, width);
    // table.setTextAlignment(TextAlignment.CENTER);
    // doc.add(table);
    // }

    public void formatPDFPage(PdfDocument pdfdoc, Document doc) {
        PageSize ps = pdfdoc.getDefaultPageSize();
        addPageNumbers(doc, 14, ps);
        addTitle(doc, 14, ps);
        // float width =
        addVotesTable(doc, ps, 6);
        // addRunningSumsTable(doc, ps, width, 6);
    }
}