package com.badmintonclub.clubmanagement.service;

import com.badmintonclub.clubmanagement.dto.report.ReportSummaryResponse;
import com.badmintonclub.clubmanagement.dto.report.ReportSummaryResponse.Debtor;
import com.badmintonclub.clubmanagement.dto.report.ReportSummaryResponse.ExpenseLine;
import com.badmintonclub.clubmanagement.dto.report.ReportSummaryResponse.IncomeLine;
import com.badmintonclub.clubmanagement.dto.report.ReportSummaryResponse.MonthPoint;
import com.badmintonclub.clubmanagement.entity.Expense;
import com.badmintonclub.clubmanagement.entity.GuestRegistration;
import com.badmintonclub.clubmanagement.entity.Payment;
import com.badmintonclub.clubmanagement.entity.Schedule;
import com.badmintonclub.clubmanagement.enums.ExpenseCategory;
import com.badmintonclub.clubmanagement.enums.PaymentMethod;
import com.badmintonclub.clubmanagement.service.ReportService.ReportData;
import org.apache.poi.ss.usermodel.BorderStyle;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellStyle;
import org.apache.poi.ss.usermodel.FillPatternType;
import org.apache.poi.ss.usermodel.Font;
import org.apache.poi.ss.usermodel.HorizontalAlignment;
import org.apache.poi.ss.usermodel.IndexedColors;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.util.CellRangeAddress;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.stereotype.Component;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;

/**
 * Ghi báo cáo thu chi ra file Excel (.xlsx) bằng Apache POI. Số liệu lấy từ ReportService.load() — cùng nguồn với
 * màn hình Báo cáo nên luôn khớp. 5 sheet: Tổng quan, Theo tháng, Chi tiết thu, Chi tiết chi, Công nợ.
 */
@Component
public class ReportExcelWriter {

    private static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("dd/MM/yyyy");
    private static final DateTimeFormatter DATE_TIME = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm");
    private static final Map<String, String> INCOME_LABELS = Map.of(
            "MONTHLY", "Phí tháng", "EXTRA", "Thu thêm", "GUEST", "Phí khách");
    private static final Map<ExpenseCategory, String> EXPENSE_LABELS = Map.of(
            ExpenseCategory.SESSION, "Tiền sân + cầu", ExpenseCategory.OTHER, "Khác");
    private static final Map<PaymentMethod, String> METHOD_LABELS = Map.of(
            PaymentMethod.CASH, "Tiền mặt", PaymentMethod.BANK_TRANSFER, "Chuyển khoản");

    public byte[] write(ReportData data, String exportedBy) {
        try (XSSFWorkbook wb = new XSSFWorkbook(); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            Styles st = new Styles(wb);
            writeOverview(wb, st, data.summary(), exportedBy);
            writeMonths(wb, st, data.summary());
            writeIncome(wb, st, data);
            writeExpenses(wb, st, data.expenses());
            writeOutstanding(wb, st, data);
            wb.write(out);
            return out.toByteArray();
        } catch (IOException e) {
            throw new UncheckedIOException("Không tạo được file Excel", e);
        }
    }

    // ---------------------------------------------------------------- Sheet "Tổng quan"
    private void writeOverview(XSSFWorkbook wb, Styles st, ReportSummaryResponse s, String exportedBy) {
        Sheet sh = wb.createSheet("Tổng quan");
        int r = title(sh, st, "BÁO CÁO THU CHI – CÂU LẠC BỘ CẦU LÔNG", 3);
        r = info(sh, st, r, "Kỳ báo cáo: " + s.from().format(DATE) + " – " + s.to().format(DATE));
        r = info(sh, st, r, "Ngày xuất: " + LocalDateTime.now().format(DATE_TIME) + " · Người xuất: " + exportedBy);
        r++;
        header(sh, st, r++, "Khoản mục", "Số lượng", "Số tiền");
        r = line(sh, st, r, "Tồn quỹ đầu kỳ", null, s.openingBalance(), st.bold, st.moneyBold);
        r = section(sh, st, r, "THU (theo ngày thu tiền)");
        for (IncomeLine l : s.income()) r = line(sh, st, r, "   " + INCOME_LABELS.get(l.source()), l.count(), l.amount(), st.text, st.money);
        r = line(sh, st, r, "Tổng thu", null, s.totalIncome(), st.bold, st.moneyBold);
        r = section(sh, st, r, "CHI (theo ngày chi)");
        for (ExpenseLine l : s.expense()) r = line(sh, st, r, "   " + EXPENSE_LABELS.get(l.category()), l.count(), l.amount(), st.text, st.money);
        r = line(sh, st, r, "Tổng chi", null, s.totalExpense(), st.bold, st.moneyBold);
        r = line(sh, st, r, "Chênh lệch thu – chi", null, s.totalIncome().subtract(s.totalExpense()), st.bold, st.moneyBold);
        r = line(sh, st, r, "Tồn quỹ cuối kỳ", null, s.closingBalance(), st.highlight, st.moneyHighlight);
        r++;
        r = section(sh, st, r, "CÔNG NỢ HIỆN TẠI (tính đến ngày xuất, không theo kỳ)");
        r = line(sh, st, r, "   Khoản thu thành viên chưa nộp", s.outstanding().paymentCount(), s.outstanding().paymentAmount(), st.text, st.money);
        r = line(sh, st, r, "   Phí khách chưa thu", s.outstanding().guestCount(), s.outstanding().guestAmount(), st.text, st.money);
        r = line(sh, st, r, "Tổng công nợ", null, s.outstanding().total(), st.bold, st.moneyBold);
        r++;
        info(sh, st, r, "Cách tính: tồn đầu kỳ = tổng thu − tổng chi trước kỳ; tồn cuối kỳ = tồn đầu kỳ + thu − chi trong kỳ. "
                + "Phí khách của buổi đã hủy không tính vào công nợ.");
        widths(sh, 44, 12, 18);
    }

    // ---------------------------------------------------------------- Sheet "Theo tháng"
    private void writeMonths(XSSFWorkbook wb, Styles st, ReportSummaryResponse s) {
        Sheet sh = wb.createSheet("Theo tháng");
        int r = title(sh, st, "THU CHI THEO THÁNG", 3);
        r = info(sh, st, r, "Kỳ báo cáo: " + s.from().format(DATE) + " – " + s.to().format(DATE));
        r++;
        int headerRow = r;
        header(sh, st, r++, "Tháng", "Thu", "Chi", "Chênh lệch");
        BigDecimal in = BigDecimal.ZERO, out = BigDecimal.ZERO;
        for (MonthPoint m : s.months()) {
            Row row = sh.createRow(r++);
            cell(row, 0, String.format("%02d/%d", m.month(), m.year()), st.text);
            cell(row, 1, m.income(), st.money);
            cell(row, 2, m.expense(), st.money);
            cell(row, 3, m.income().subtract(m.expense()), st.money);
            in = in.add(m.income());
            out = out.add(m.expense());
        }
        Row total = sh.createRow(r);
        cell(total, 0, "Tổng", st.bold);
        cell(total, 1, in, st.moneyBold);
        cell(total, 2, out, st.moneyBold);
        cell(total, 3, in.subtract(out), st.moneyBold);
        sh.createFreezePane(0, headerRow + 1);
        widths(sh, 14, 18, 18, 18);
    }

    // ---------------------------------------------------------------- Sheet "Chi tiết thu"
    private void writeIncome(XSSFWorkbook wb, Styles st, ReportData data) {
        record IncomeRow(LocalDateTime paidAt, String type, String content, String payer, String method, BigDecimal amount) {
        }
        List<IncomeRow> rows = new ArrayList<>();
        for (Payment p : data.paidPayments()) {
            rows.add(new IncomeRow(p.getPaidAt(), INCOME_LABELS.get(p.getPaymentType().name()), p.getBatch().getDescription(),
                    p.getUser().getFullName(), METHOD_LABELS.getOrDefault(p.getPaymentMethod(), ""), p.getAmount()));
        }
        for (GuestRegistration g : data.paidGuests()) {
            String payer = g.getGuest().getFullName()
                    + (g.getInvitedBy() != null ? " (khách của " + g.getInvitedBy().getFullName() + ")" : " (khách tự liên hệ)");
            String collector = g.getCollectedBy() != null ? "Người thu: " + g.getCollectedBy().getFullName() : "";
            rows.add(new IncomeRow(g.getPaidAt(), "Phí khách", "Phí khách – " + scheduleLabel(g.getSchedule()),
                    payer, collector, g.getFee()));
        }
        rows.sort(Comparator.comparing(IncomeRow::paidAt));

        Sheet sh = wb.createSheet("Chi tiết thu");
        int r = title(sh, st, "CHI TIẾT THU (đã thu trong kỳ)", 6);
        r++;
        int headerRow = r;
        header(sh, st, r++, "STT", "Ngày thu", "Loại", "Nội dung", "Người nộp", "Hình thức / Người thu", "Số tiền");
        BigDecimal total = BigDecimal.ZERO;
        int i = 1;
        for (IncomeRow x : rows) {
            Row row = sh.createRow(r++);
            cell(row, 0, i++, st.center);
            cell(row, 1, x.paidAt().format(DATE_TIME), st.text);
            cell(row, 2, x.type(), st.text);
            cell(row, 3, x.content(), st.text);
            cell(row, 4, x.payer(), st.text);
            cell(row, 5, x.method(), st.text);
            cell(row, 6, x.amount(), st.money);
            total = total.add(x.amount());
        }
        if (rows.isEmpty()) cell(sh.createRow(r++), 0, "Không có khoản thu nào trong kỳ", st.muted);
        totalRow(sh, st, r, 6, total);
        if (!rows.isEmpty()) sh.setAutoFilter(new CellRangeAddress(headerRow, headerRow + rows.size(), 0, 6));
        sh.createFreezePane(0, headerRow + 1);
        widths(sh, 6, 17, 12, 40, 34, 26, 15);
    }

    // ---------------------------------------------------------------- Sheet "Chi tiết chi"
    private void writeExpenses(XSSFWorkbook wb, Styles st, List<Expense> expenses) {
        Sheet sh = wb.createSheet("Chi tiết chi");
        int r = title(sh, st, "CHI TIẾT CHI (trong kỳ)", 7);
        r++;
        int headerRow = r;
        header(sh, st, r++, "STT", "Ngày chi", "Loại", "Nội dung", "Buổi chơi", "Ghi chú", "Người ghi", "Số tiền");
        BigDecimal total = BigDecimal.ZERO;
        int i = 1;
        for (Expense e : expenses) {
            Row row = sh.createRow(r++);
            cell(row, 0, i++, st.center);
            cell(row, 1, e.getExpenseDate().format(DATE), st.text);
            cell(row, 2, EXPENSE_LABELS.get(e.getCategory()), st.text);
            cell(row, 3, e.getDescription(), st.text);
            cell(row, 4, e.getSchedule() != null ? scheduleLabel(e.getSchedule()) : "", st.text);
            cell(row, 5, e.getNote() != null ? e.getNote() : "", st.text);
            cell(row, 6, e.getCreatedBy().getFullName(), st.text);
            cell(row, 7, e.getAmount(), st.money);
            total = total.add(e.getAmount());
        }
        if (expenses.isEmpty()) cell(sh.createRow(r++), 0, "Không có khoản chi nào trong kỳ", st.muted);
        totalRow(sh, st, r, 7, total);
        if (!expenses.isEmpty()) sh.setAutoFilter(new CellRangeAddress(headerRow, headerRow + expenses.size(), 0, 7));
        sh.createFreezePane(0, headerRow + 1);
        widths(sh, 6, 12, 15, 36, 32, 36, 20, 15);
    }

    // ---------------------------------------------------------------- Sheet "Công nợ"
    private void writeOutstanding(XSSFWorkbook wb, Styles st, ReportData data) {
        ReportSummaryResponse.Outstanding o = data.summary().outstanding();
        Sheet sh = wb.createSheet("Công nợ");
        int r = title(sh, st, "CÔNG NỢ HIỆN TẠI", 7);
        r = info(sh, st, r, "Tính đến " + LocalDateTime.now().format(DATE_TIME)
                + ". Phí khách tính cho người dẫn khách; không tính khách của buổi đã hủy.");
        r++;
        r = section(sh, st, r, "Tổng hợp theo thành viên");
        header(sh, st, r++, "STT", "Thành viên", "Email", "Số khoản chưa nộp", "Tiền chưa nộp", "Số khách chưa thu",
                "Phí khách chưa thu", "Tổng nợ");
        int i = 1;
        for (Debtor d : o.debtors()) {
            Row row = sh.createRow(r++);
            cell(row, 0, i++, st.center);
            cell(row, 1, d.name(), st.text);
            cell(row, 2, d.email() != null ? d.email() : "", st.text);
            cell(row, 3, d.paymentCount(), st.center);
            cell(row, 4, d.paymentAmount(), st.money);
            cell(row, 5, d.guestCount(), st.center);
            cell(row, 6, d.guestAmount(), st.money);
            cell(row, 7, d.total(), st.moneyBold);
        }
        if (o.debtors().isEmpty()) cell(sh.createRow(r++), 0, "Không còn ai nợ", st.muted);
        totalRow(sh, st, r++, 7, o.total());
        r++;

        r = section(sh, st, r, "Khoản thu thành viên chưa nộp");
        header(sh, st, r++, "STT", "Thành viên", "Khoản thu", "Loại", "Ngày tạo", "", "", "Số tiền");
        i = 1;
        for (Payment p : data.unpaidPayments()) {
            Row row = sh.createRow(r++);
            cell(row, 0, i++, st.center);
            cell(row, 1, p.getUser().getFullName(), st.text);
            cell(row, 2, p.getBatch().getDescription(), st.text);
            cell(row, 3, INCOME_LABELS.get(p.getPaymentType().name()), st.text);
            cell(row, 4, p.getCreatedAt() != null ? p.getCreatedAt().format(DATE) : "", st.text);
            cell(row, 7, p.getAmount(), st.money);
        }
        if (data.unpaidPayments().isEmpty()) cell(sh.createRow(r++), 0, "Không có", st.muted);
        r++;

        r = section(sh, st, r, "Phí khách chưa thu");
        header(sh, st, r++, "STT", "Khách", "Buổi chơi", "Người dẫn", "", "", "", "Phí");
        i = 1;
        for (GuestRegistration g : data.unpaidGuests()) {
            Row row = sh.createRow(r++);
            cell(row, 0, i++, st.center);
            cell(row, 1, g.getGuest().getFullName(), st.text);
            cell(row, 2, scheduleLabel(g.getSchedule()), st.text);
            cell(row, 3, g.getInvitedBy() != null ? g.getInvitedBy().getFullName() : "Tự liên hệ CLB", st.text);
            cell(row, 7, g.getFee(), st.money);
        }
        if (data.unpaidGuests().isEmpty()) cell(sh.createRow(r), 0, "Không có", st.muted);
        widths(sh, 6, 28, 34, 22, 14, 16, 18, 16);
    }

    // ---------------------------------------------------------------- Tiện ích
    private static String scheduleLabel(Schedule s) {
        return s.getTitle() + " (" + s.getPlayDate().format(DATE) + ")";
    }

    private static int title(Sheet sh, Styles st, String text, int lastCol) {
        Row row = sh.createRow(0);
        row.setHeightInPoints(24);
        cell(row, 0, text, st.title);
        sh.addMergedRegion(new CellRangeAddress(0, 0, 0, lastCol));
        return 1;
    }

    private static int info(Sheet sh, Styles st, int r, String text) {
        cell(sh.createRow(r), 0, text, st.muted);
        return r + 1;
    }

    private static int section(Sheet sh, Styles st, int r, String text) {
        cell(sh.createRow(r), 0, text, st.section);
        return r + 1;
    }

    private static void header(Sheet sh, Styles st, int r, String... labels) {
        Row row = sh.createRow(r);
        for (int c = 0; c < labels.length; c++) cell(row, c, labels[c], st.header);
    }

    private static int line(Sheet sh, Styles st, int r, String label, Long count, BigDecimal amount,
                            CellStyle labelStyle, CellStyle moneyStyle) {
        Row row = sh.createRow(r);
        cell(row, 0, label, labelStyle);
        if (count != null) cell(row, 1, count, st.center);
        else cell(row, 1, "", labelStyle);
        cell(row, 2, amount, moneyStyle);
        return r + 1;
    }

    private static void totalRow(Sheet sh, Styles st, int r, int moneyCol, BigDecimal total) {
        Row row = sh.createRow(r);
        cell(row, moneyCol - 1, "Tổng cộng", st.bold);
        cell(row, moneyCol, total, st.moneyBold);
    }

    private static void widths(Sheet sh, int... chars) {
        for (int c = 0; c < chars.length; c++) sh.setColumnWidth(c, chars[c] * 256);
    }

    private static void cell(Row row, int col, String value, CellStyle style) {
        Cell c = row.createCell(col);
        c.setCellValue(value);
        c.setCellStyle(style);
    }

    private static void cell(Row row, int col, long value, CellStyle style) {
        Cell c = row.createCell(col);
        c.setCellValue(value);
        c.setCellStyle(style);
    }

    private static void cell(Row row, int col, BigDecimal value, CellStyle style) {
        Cell c = row.createCell(col);
        c.setCellValue(value != null ? value.doubleValue() : 0);
        c.setCellStyle(style);
    }

    // Kiểu ô dùng chung (POI giới hạn số CellStyle mỗi file -> tạo 1 lần)
    private static final class Styles {
        final CellStyle title, muted, section, header, text, center, bold, money, moneyBold, highlight, moneyHighlight;

        Styles(XSSFWorkbook wb) {
            Font titleFont = wb.createFont();
            titleFont.setBold(true);
            titleFont.setFontHeightInPoints((short) 14);
            Font boldFont = wb.createFont();
            boldFont.setBold(true);
            Font mutedFont = wb.createFont();
            mutedFont.setItalic(true);
            mutedFont.setColor(IndexedColors.GREY_50_PERCENT.getIndex());
            Font headerFont = wb.createFont();
            headerFont.setBold(true);
            headerFont.setColor(IndexedColors.WHITE.getIndex());
            short moneyFormat = wb.createDataFormat().getFormat("#,##0\" ₫\";-#,##0\" ₫\"");

            title = wb.createCellStyle();
            title.setFont(titleFont);
            muted = wb.createCellStyle();
            muted.setFont(mutedFont);
            section = wb.createCellStyle();
            section.setFont(boldFont);
            header = bordered(wb);
            header.setFont(headerFont);
            header.setFillForegroundColor(IndexedColors.SEA_GREEN.getIndex());
            header.setFillPattern(FillPatternType.SOLID_FOREGROUND);
            header.setAlignment(HorizontalAlignment.CENTER);
            header.setWrapText(true);
            text = bordered(wb);
            center = bordered(wb);
            center.setAlignment(HorizontalAlignment.CENTER);
            bold = bordered(wb);
            bold.setFont(boldFont);
            money = bordered(wb);
            money.setDataFormat(moneyFormat);
            moneyBold = bordered(wb);
            moneyBold.setDataFormat(moneyFormat);
            moneyBold.setFont(boldFont);
            highlight = bordered(wb);
            highlight.setFont(boldFont);
            highlight.setFillForegroundColor(IndexedColors.LIGHT_GREEN.getIndex());
            highlight.setFillPattern(FillPatternType.SOLID_FOREGROUND);
            moneyHighlight = bordered(wb);
            moneyHighlight.cloneStyleFrom(highlight);
            moneyHighlight.setDataFormat(moneyFormat);
        }

        private static CellStyle bordered(XSSFWorkbook wb) {
            CellStyle s = wb.createCellStyle();
            s.setBorderTop(BorderStyle.THIN);
            s.setBorderBottom(BorderStyle.THIN);
            s.setBorderLeft(BorderStyle.THIN);
            s.setBorderRight(BorderStyle.THIN);
            return s;
        }
    }
}
