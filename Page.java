import com.itextpdf.kernel.colors.ColorConstants;
import com.itextpdf.kernel.geom.PageSize;
import com.itextpdf.layout.Document;
import com.itextpdf.layout.borders.Border;
import com.itextpdf.layout.borders.SolidBorder;
import com.itextpdf.layout.borders.DoubleBorder;
import com.itextpdf.layout.element.Cell;
import com.itextpdf.layout.element.Paragraph;
import com.itextpdf.layout.element.Table;
import com.itextpdf.layout.property.HorizontalAlignment;
import com.itextpdf.layout.property.VerticalAlignment;
import com.itextpdf.layout.property.TextAlignment;

import java.util.ArrayList;

import javax.naming.NameAlreadyBoundException;

public class Page {
    // ID of the page (which number)
    private final int pageID;
    private final int cols; // number of columns in the table
    // index to start at CVR_lines
    private final int votes_line_start_i;
    private final int num_lines_on_page;
    private final ArrayList<ArrayList<Integer>> partial_sums;
    private final ArrayList<ArrayList<Integer>> prev_running_sums;
    private final Contest[] cs; // contest sheet
    private final int num_pages; // number of pages

    public Page(Contest[] cs, int pageID, int votes_line_start_i, ArrayList<ArrayList<Integer>> partial_sums,
            ArrayList<ArrayList<Integer>> prev_running_sums, int num_pages) {
        this.pageID = pageID;
        this.votes_line_start_i = votes_line_start_i;
        this.cs = cs;
        int cols = 2;
        for (Contest c : cs) {
            cols += c.cols();
        }
        this.cols = cols;
        this.partial_sums = partial_sums;
        this.prev_running_sums = prev_running_sums;
        int lines = cs[0].ballots() - votes_line_start_i;
        this.num_lines_on_page = lines > cs[0].BALLOTS_PER_PAGE() ? cs[0].BALLOTS_PER_PAGE() : lines;
        this.num_pages = num_pages;
    }

    private Table createPageNumberTable(float left, float bottom, float fontSize) {
        Table table = new Table(1);
        Cell cell = new Cell();
        cell.add(new Paragraph(pageID + " of " + num_pages));
        cell.setBorder(new SolidBorder(ColorConstants.BLACK, 1));
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
        // table.setWidthPercentage();
        int len = cs[0].title().length();
        table.setWidth(len);
        float top_row = ps.getHeight() - 2 * fontSize;
        table.setTextAlignment(TextAlignment.CENTER);
        Paragraph p = new Paragraph(cs[0].title());
        Cell cell = new Cell();
        cell.setBorder(Border.NO_BORDER);
        cell.add(p);
        while (len * fontSize >= ps.getWidth() * 2) {
            fontSize--;
        }
        table.setFontSize(fontSize);
        float middle_with_offset = ps.getWidth() / 2 - (fontSize * len / 2);
        table.setFixedPosition(pageID, middle_with_offset, top_row, len * fontSize);
        table.addCell(cell);
        doc.add(table);
    }

    private void addContestsNames(Table table) {
        Border border = new DoubleBorder(2);
        Cell cell = new Cell(0, 2).add(new Paragraph("Contests"));
        cell.setVerticalAlignment(VerticalAlignment.BOTTOM);
        table.addHeaderCell(cell);
        for (int c = 0; c < cs.length; c++) {
            cell = new Cell(1, cs[c].cols());
            cell.add(new Paragraph(cs[c].contest_name()));
            if (c != cs.length - 1) {
                cell.setBorderRight(border);
            }
            table.addHeaderCell(cell);
        }
    }

    public void addTitlesToTable(Table table) {
        addContestsNames(table);
        addParties(table);
        table.startNewRow();
        addCandidateNamesRow(table);
    }

    private void addParties(Table table) {
        if (!has_parties(this.cs))
            return;

        table.addCell(new Cell(0, 2).add(new Paragraph("Party")));
        addPartiesRow(table);
    }

    private static boolean has_parties(Contest[] contests) {
        for (Contest c : contests) {
            for (int i = 0; i < c.cols(); i++) {
                if (!c.party(i).equals("")) {
                    return true;
                }
            }
        }
        return false;
    }

    private void addPartiesRow(Table table) {
        DoubleBorder separation_border = new DoubleBorder(2);
        for (int c = 0; c < cs.length; c++) {
            for (int i = 0; i < cs[c].cols(); i++) {
                Cell cell = new Cell().add(new Paragraph(cs[c].party(i)));
                setBorderForNewContest(separation_border, c, i, cell);
                table.addCell(cell);
            }
        }
    }

    private void addCandidateNamesRow(Table table) {
        Border border = new SolidBorder(2);
        DoubleBorder separation_border = new DoubleBorder(2);
        Cell cell = candidateRowCell(border, "#");
        table.addCell(cell);
        table.addCell(candidateRowCell(border, "Ballot ID"));
        for (int c = 0; c < cs.length; c++) {
            for (int i = 0; i < cs[c].cols(); i++) {
                String candidate = splitIntoLines(cs[c].candidate(i));
                cell = new Cell().add(new Paragraph(candidate));
                cell.setBorderBottom(border);
                cell.setVerticalAlignment(VerticalAlignment.BOTTOM);
                setBorderForNewContest(separation_border, c, i, cell);
                table.addCell(cell);
            }
        }
    }

    private Cell candidateRowCell(Border border, String contents) {
        Cell cell;
        cell = new Cell().add(new Paragraph(contents));
        cell.setBorderBottom(border);
        cell.setTextAlignment(TextAlignment.CENTER);
        cell.setVerticalAlignment(VerticalAlignment.BOTTOM);
        return cell;
    }

    private static String splitIntoLines(String candidate) {
        candidate = candidate.replaceAll(" ", "\n");
        return candidate.replaceAll("\n/\n", " /\n");
    }

    private void setBorderForNewContest(Border separation_border, int c, int i, Cell cell) {
        if (c < cs.length - 1 && i == cs[c].cols() - 1) {
            cell.setBorderRight(separation_border);
        }
    }

    private int partialSum(int contest, int col) {
        return this.partial_sums.get(contest).get(col);
    }

    private int prevRunningSum(int contest, int col) {
        return this.prev_running_sums.get(contest).get(col);
    }

    private int addVotesToTable(Table table) {
        int possible_votes = 0;
        DoubleBorder separation_border = new DoubleBorder(2);

        for (int i = votes_line_start_i; i < votes_line_start_i + num_lines_on_page; i++) {
            table.startNewRow();
            Cell count_vote = new Cell().add(new Paragraph("1"));
            possible_votes++;
            count_vote.setBorderRight(separation_border);
            table.addCell(count_vote);
            table.addCell(new Cell().add(new Paragraph(cs[0].getImprintedID(i))));
            for (int c = 0; c < cs.length; c++) {
                VoteCount vc = cs[c].getVoteCount(i);
                for (int j = 0; j < cs[c].cols(); j++) {
                    String vote = cs[c].getVote(j, i);
                    Cell cell = new Cell().add(new Paragraph(vote));
                    if (vc == VoteCount.UNDER_VOTE) {
                        cell.setBackgroundColor(ColorConstants.LIGHT_GRAY);
                    } else if (vc == VoteCount.OVER_VOTE) {
                        cell.setBackgroundColor(ColorConstants.RED);
                    }
                    setBorderForNewContest(separation_border, c, j, cell);
                    table.addCell(cell);
                }
            }
        }
        return possible_votes;
    }

    private void addSumsToTable(Table table, int possible_votes) {
        float borderThickness = 2;
        addPartialSums(table, possible_votes, borderThickness);
        table.startNewRow();
        addPreviousSums(table);
        table.startNewRow();
        addRunningSums(table);
    }

    private void addRunningSums(Table table) {
        Border separation_border = new DoubleBorder(2);
        Cell rps = new Cell().add(new Paragraph(Integer.toString(votes_line_start_i)));
        rps.setBorderRight(separation_border);
        table.addCell(rps);
        Cell running = new Cell().add(new Paragraph("Running"));
        table.addCell(running);
        for (int c = 0; c < cs.length; c++) {
            for (int i = 0; i < cs[c].cols(); i++) {
                Paragraph p = new Paragraph(Integer.toString(prevRunningSum(c, i) + partialSum(c, i)));
                Cell cell = new Cell().add(p);
                setBorderForNewContest(separation_border, c, i, cell);
                table.addCell(cell);
            }
        }
    }

    private void addPreviousSums(Table table) {
        Border separation_border = new DoubleBorder(2);
        Cell page_num = new Cell().add(new Paragraph(Integer.toString(pageID - 1)));
        page_num.setBorderRight(separation_border);
        table.addCell(page_num);
        Cell previous = new Cell().add(new Paragraph("Previous"));
        table.addCell(previous);
        for (int c = 0; c < cs.length; c++) {
            for (int i = 0; i < cs[c].cols(); i++) {
                Paragraph p = new Paragraph(Integer.toString(prevRunningSum(c, i)));
                Cell cell = new Cell().add(p);
                setBorderForNewContest(separation_border, c, i, cell);
                table.addCell(cell);
            }
        }
    }

    private void addPartialSums(Table table, int possible_votes, float borderThickness) {
        Cell pvs = new Cell().add(new Paragraph(Integer.toString(possible_votes)));
        Border separation_border = new DoubleBorder(2);
        pvs.setBorderRight(separation_border);
        pvs.setBorderTop(new SolidBorder(borderThickness));
        table.addCell(pvs);
        Cell partial = new Cell().add(new Paragraph("Partial"));
        partial.setBorderTop(new SolidBorder(borderThickness));
        table.addCell(partial);
        for (int contest = 0; contest < cs.length; contest++) {
            for (int i = 0; i < cs[contest].cols(); i++) {
                Paragraph p = new Paragraph(Integer.toString(partialSum(contest, i)));
                Cell c = new Cell().add(p);
                c.setBorderTop(new SolidBorder(borderThickness));
                setBorderForNewContest(separation_border, contest, i, c);
                table.addCell(c);
            }
        }
    }

    public void addVotesTable(Document doc, PageSize ps, float fontSize) {
        Table table = new Table(this.cols);
        addTitlesToTable(table);
        int possible_votes = addVotesToTable(table);
        table.setFontSize(fontSize);
        table.startNewRow();
        addSumsToTable(table, possible_votes);
        table.setVerticalAlignment(VerticalAlignment.MIDDLE);
        table.setHorizontalAlignment(HorizontalAlignment.CENTER);
        table.setAutoLayout();
        table.setRelativePosition(0, 30, 0, 0);
        table.setTextAlignment(TextAlignment.CENTER);
        doc.add(table);
    }

    public void formatPDFPage(PageSize ps, Document doc) {
        addPageNumbers(doc, 14, ps);
        addTitle(doc, 14, ps);
        addVotesTable(doc, ps, 6);
    }
}