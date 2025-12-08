package com.aiinvest.api;

import com.aiinvest.domain.Position;
import com.aiinvest.repo.PositionRepository;
import com.aiinvest.api.dto.UpdatePositionRequest;
import com.aiinvest.domain.PositionSnapshot;
import com.aiinvest.repo.PositionSnapshotRepository;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.transaction.annotation.Transactional;
import jakarta.validation.Valid;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.time.LocalDate;
import java.util.*;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.apache.poi.ss.usermodel.*;
import jakarta.servlet.http.HttpServletResponse;

@RestController
@RequestMapping("/api/positions")
public class PositionController {
    private final PositionRepository repo;
    private final PositionSnapshotRepository snapshotRepository;
    public PositionController(PositionRepository repo, PositionSnapshotRepository snapshotRepository) { this.repo = repo; this.snapshotRepository = snapshotRepository; }

    @GetMapping("/current")
    public List<Position> current() {
        return repo.findByCreatedAtIsNotNullOrderByCreatedAtDesc();
    }

    @GetMapping("/ai")
    public List<Position> aiCurrent() {
        return repo.findByCreatedAtIsNotNullOrderByCreatedAtDesc();
    }

    @GetMapping("/history")
    public List<Position> history() {
        return repo.findAll();
    }

    @GetMapping("/snapshots")
    public Map<String, Object> snapshots(@RequestParam(defaultValue = "7") int days) {
        LocalDate end = LocalDate.now();
        LocalDate start = end.minusDays(days - 1L);
        List<PositionSnapshot> list = snapshotRepository.findBySnapshotDateBetweenOrderBySnapshotDate(start, end);
        Map<LocalDate, BigDecimal> dailyTotal = new LinkedHashMap<>();
        Map<String, Map<LocalDate, BigDecimal>> bySymbol = new LinkedHashMap<>();
        for (PositionSnapshot ps : list) {
            dailyTotal.put(ps.getSnapshotDate(), dailyTotal.getOrDefault(ps.getSnapshotDate(), BigDecimal.ZERO).add(ps.getTotalUsd()));
            bySymbol.computeIfAbsent(ps.getSymbol(), k -> new LinkedHashMap<>())
                    .put(ps.getSnapshotDate(), ps.getTotalUsd());
        }
        List<Map<String, Object>> totals = new ArrayList<>();
        dailyTotal.forEach((date, total) -> {
            Map<String, Object> m = new HashMap<>();
            m.put("date", date.toString());
            m.put("totalUsd", total);
            totals.add(m);
        });
        totals.sort(Comparator.comparing(o -> LocalDate.parse((String) o.get("date"))));

        List<Map<String, Object>> series = new ArrayList<>();
        bySymbol.forEach((symbol, mp) -> {
            Map<String, Object> s = new HashMap<>();
            s.put("symbol", symbol);
            List<Map<String, Object>> points = new ArrayList<>();
            mp.forEach((date, val) -> {
                Map<String, Object> p = new HashMap<>();
                p.put("date", date.toString());
                p.put("totalUsd", val);
                points.add(p);
            });
            points.sort(Comparator.comparing(o -> LocalDate.parse((String) o.get("date"))));
            s.put("points", points);
            series.add(s);
        });
        Map<String, Object> resp = new HashMap<>();
        resp.put("totals", totals);
        resp.put("series", series);
        return resp;
    }

    @GetMapping("/export")
    public void exportExcel(HttpServletResponse response) {
        try (XSSFWorkbook workbook = new XSSFWorkbook()) {
            Sheet sheet = workbook.createSheet("positions");
            Row header = sheet.createRow(0);
            header.createCell(0).setCellValue("symbol");
            header.createCell(1).setCellValue("percent");
            header.createCell(2).setCellValue("amountUsd");
            header.createCell(3).setCellValue("createdAt");

            List<Position> positions = repo.findByCreatedAtIsNotNullOrderByCreatedAtDesc();
            for (int i = 0; i < positions.size(); i++) {
                Position p = positions.get(i);
                Row row = sheet.createRow(i + 1);
                row.createCell(0).setCellValue(p.getSymbol());
                row.createCell(1).setCellValue(p.getPercent().doubleValue());
                row.createCell(2).setCellValue(p.getAmountUsd().doubleValue());
                row.createCell(3).setCellValue(p.getCreatedAt().toString());
            }
            for (int i = 0; i < 4; i++) sheet.autoSizeColumn(i);
            response.setHeader("Content-Disposition", "attachment; filename=positions.xlsx");
            response.setContentType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");
            workbook.write(response.getOutputStream());
            response.flushBuffer();
        } catch (Exception e) {
            throw new RuntimeException("export excel failed", e);
        }
    }

    @PostMapping("/update")
    @Transactional
    public ResponseEntity<?> update(@RequestBody @Valid List<UpdatePositionRequest> items) {
        if (items == null || items.isEmpty()) {
            return ResponseEntity.badRequest().body("positions required");
        }
        java.math.BigDecimal percentTotal = java.math.BigDecimal.ZERO;
        java.math.BigDecimal amountTotal = java.math.BigDecimal.ZERO;
        repo.deleteAll();
        OffsetDateTime now = OffsetDateTime.now();
        for (UpdatePositionRequest r : items) {
            if (r.getSymbol() == null || r.getSymbol().trim().isEmpty()) {
                return ResponseEntity.badRequest().body("symbol cannot be empty");
            }
            Position p = new Position();
            p.setSymbol(r.getSymbol());
            p.setPercent(r.getPercent());
            p.setAmountUsd(r.getAmountUsd());
            p.setCreatedAt(now);
            repo.save(p);
            percentTotal = percentTotal.add(r.getPercent());
            amountTotal = amountTotal.add(r.getAmountUsd());
        }
        if (percentTotal.compareTo(new java.math.BigDecimal("99")) < 0 || percentTotal.compareTo(new java.math.BigDecimal("101")) > 0) {
            return ResponseEntity.badRequest().body("total percent must be close to 100 (99-101)");
        }
        if (amountTotal.compareTo(java.math.BigDecimal.ZERO) <= 0) {
            return ResponseEntity.badRequest().body("total amount must be positive");
        }
        return ResponseEntity.ok().build();
    }
}
