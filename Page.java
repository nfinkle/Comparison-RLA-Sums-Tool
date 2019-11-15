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

import java.util.HashMap;

class Page {
    // ID of the page (which number)
    private final int pageID;
    // index to start at CVR_lines
    private final int votes_line_start_i;
    private final int num_lines_on_page;
    private int[] partial_sums;
    private int[] running_sums;
    private final ContestSheets cs; // contest sheet

    public Page(ContestSheets cs, int pageID, int votes_line_start_i, int[] partial_sums, int[] running_sums) {
        this.pageID = pageID;
        this.votes_line_start_i = votes_line_start_i;
        this.cs = cs;
        this.partial_sums = partial_sums;
        this.running_sums = running_sums;
        int lines = cs.ballots - votes_line_start_i;
        this.num_lines_on_page = lines > cs.BALLOTS_PER_PAGE() ? cs.BALLOTS_PER_PAGE() : lines;
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
        int len = cs.title.length();
        table.setWidth(len);
        float middle_with_offset = ps.getWidth() / 2 - (fontSize * len / 2);
        float top_row = ps.getHeight() - 2 * fontSize;
        table.setFixedPosition(pageID, middle_with_offset, top_row, len * fontSize);
        table.setTextAlignment(TextAlignment.CENTER);
        Paragraph p = new Paragraph(cs.title);
        p.setFontSize(fontSize);
        Cell cell = new Cell();
        cell.setBorder(Border.NO_BORDER);
        cell.add(p);
        table.addCell(cell);
        doc.add(table);
    }

    public float addTitlesToTable(Table table, float bottomBorderThickness) {
        float width = addPartiesRow(table);
        table.startNewRow();
        width = Math.max(width, addCandidateNamesRow(table, bottomBorderThickness));
        return width;
    }

    private float addPartiesRow(Table table) {
        float width = 0;
        table.addCell(new Cell()); // skip the imprintedID cell
        for (int i = 0; i < cs.cols; i++) {
            String party = cs.party(i);
            width += party.length();
            Cell c = new Cell().add(new Paragraph(party));
            table.addCell(c);
        }
        return width;
    }

    private float addCandidateNamesRow(Table table, float bottomBorderThickness) {
        float width = 0;
        Cell c = new Cell(); // skip the imprintedID cell
        c.setBorderBottom(new SolidBorder(bottomBorderThickness));
        table.addCell(c);
        for (int i = 0; i < cs.cols; i++) {
            String candidate = cs.candidate(i);
            width += candidate.length();
            c = new Cell().add(new Paragraph(candidate));
            c.setBorderBottom(new SolidBorder(bottomBorderThickness));
            table.addCell(c);
        }
        return width;
    }

    private void addVotesToTable(Table table) {
        for (int i = votes_line_start_i; i < votes_line_start_i + this.cs.BALLOTS_PER_PAGE()
                && i < this.cs.ballots; i++) {
            table.startNewRow();
            table.addCell(new Cell().add(new Paragraph(cs.getImprintedID(i))));
            VoteCount vc = cs.getVoteCount(i);
            for (int j = 0; j < this.cs.cols; j++) {
                Cell c = new Cell().add(new Paragraph(cs.getVote(j, i)));
                if (vc == VoteCount.UNDER_VOTE) {
                    c.setBackgroundColor(ColorConstants.LIGHT_GRAY);
                } else if (vc == VoteCount.OVER_VOTE) {
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
        Table table = new Table(cs.cols);
        float width = addTitlesToTable(table, 2);
        addVotesToTable(table);
        table.setFontSize(fontSize);
        table.startNewRow();
        addSumsToTable(table, 2);
        table.setHorizontalAlignment(HorizontalAlignment.CENTER);
        table.setFixedPosition(pageID, ps.getWidth() / 2 - width / 2, cs.BALLOTS_PER_PAGE(), cs.title.length());
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