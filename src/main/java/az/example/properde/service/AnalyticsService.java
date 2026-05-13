package az.example.properde.service;

import az.example.properde.dao.entity.Analytics;
import az.example.properde.dao.enums.LeadSource;
import az.example.properde.dao.enums.LeadStatus;
import az.example.properde.repository.AnalyticsRepository;
import az.example.properde.repository.LeadRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.format.TextStyle;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class AnalyticsService {
    private final AnalyticsRepository analyticsRepo;
    private final LeadRepository leadRepository;

    @Transactional
    public void incrementVisit() {
        LocalDate today = LocalDate.now();
        Analytics stats = analyticsRepo.findByVisitDate(today)
                .orElseGet(() -> Analytics.builder()
                        .visitDate(today)
                        .visitCount(0L)
                        .build());
        stats.setVisitCount(Optional.ofNullable(stats.getVisitCount()).orElse(0L) + 1);
        analyticsRepo.save(stats);
    }

    @Transactional(readOnly = true)
    public Map<String, Object> getDashboardStats() {
        Map<String, Object> data = new HashMap<>();
        LocalDate today = LocalDate.now();
        LocalDate weekStart = today.minusDays(6);
        LocalDate yearStart = LocalDate.of(today.getYear(), 1, 1);

        Map<LocalDate, Long> visitsByDate = analyticsRepo.findAllByVisitDateBetween(weekStart, today)
                .stream()
                .collect(Collectors.toMap(Analytics::getVisitDate, a -> Optional.ofNullable(a.getVisitCount()).orElse(0L), Long::sum));

        List<Map<String, Object>> weekly = new ArrayList<>();
        for (int i = 0; i < 7; i++) {
            LocalDate day = weekStart.plusDays(i);
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("dayName", day.getDayOfWeek().getDisplayName(TextStyle.SHORT, Locale.ENGLISH));
            row.put("date", day.toString());
            row.put("ziyaret", visitsByDate.getOrDefault(day, 0L));
            row.put("sifaris", leadRepository.countByCreatedAtBetween(day.atStartOfDay(), day.plusDays(1).atStartOfDay()));
            weekly.add(row);
        }

        List<Map<String, Object>> yearly = new ArrayList<>();
        for (int month = 1; month <= 12; month++) {
            LocalDate start = LocalDate.of(today.getYear(), month, 1);
            LocalDate end = start.plusMonths(1);
            long monthVisits = analyticsRepo.findAllByVisitDateBetween(start, end.minusDays(1))
                    .stream()
                    .mapToLong(a -> Optional.ofNullable(a.getVisitCount()).orElse(0L))
                    .sum();
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("month", start.getMonth().getDisplayName(TextStyle.SHORT, Locale.ENGLISH));
            row.put("ziyaret", monthVisits);
            row.put("sifaris", leadRepository.countByCreatedAtBetween(start.atStartOfDay(), end.atStartOfDay()));
            yearly.add(row);
        }

        data.put("monthlyOrders", leadRepository.countByCreatedAtBetween(today.withDayOfMonth(1).atStartOfDay(), today.plusDays(1).atStartOfDay()));
        data.put("weeklyVisits", weekly);
        data.put("yearlyStats", yearly);
        data.put("totalLeads", leadRepository.count());
        data.put("totalOrders", leadRepository.countBySource(LeadSource.ORDER.name()));
        data.put("promoRequests", leadRepository.countBySource(LeadSource.DISCOUNT.name()));
        data.put("visualizationRequests", leadRepository.countBySource(LeadSource.VISUAL.name()));
        data.put("measurementRequests", leadRepository.countBySource(LeadSource.MEASURE.name()));
        data.put("wishlistRequests", leadRepository.countBySource(LeadSource.HEART.name()));
        data.put("contactedLeads", leadRepository.countByStatus(LeadStatus.CONTACTED.name()));
        data.put("newLeads", leadRepository.countByStatus(LeadStatus.NEW.name()));
        data.put("totalOrderAmount", Optional.ofNullable(leadRepository.sumTotalAmountBySource(LeadSource.ORDER.name())).orElse(0.0));
        data.put("totalVisits", analyticsRepo.findAllByVisitDateBetween(yearStart, today)
                .stream()
                .mapToLong(a -> Optional.ofNullable(a.getVisitCount()).orElse(0L))
                .sum());
        data.put("leadsBySource", toChartRows(leadRepository.countGroupedBySource(), "source"));
        data.put("leadsByStatus", toChartRows(leadRepository.countGroupedByStatus(), "status"));
        data.put("leadsByReferrer", toChartRows(leadRepository.countGroupedByReferrer(), "referrer"));
        return data;
    }

    private List<Map<String, Object>> toChartRows(List<Object[]> rows, String labelKey) {
        if (rows == null || rows.isEmpty()) {
            return List.of();
        }
        return rows.stream().map(row -> {
            Map<String, Object> item = new LinkedHashMap<>();
            Object label = row[0];
            if ("source".equals(labelKey)) {
                label = LeadSource.fromString(label == null ? null : String.valueOf(label)).canonical().name();
            }
            item.put(labelKey, label == null ? "UNKNOWN" : String.valueOf(label));
            item.put("count", row[1]);
            return item;
        }).toList();
    }
}
